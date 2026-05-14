package com.adaptivetext.measurer

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle as ComposeFontStyle
import androidx.compose.ui.text.font.FontWeight as ComposeFontWeight
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import com.facebook.proguard.annotations.DoNotStrip
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/**
 * Intrinsic-size measurer for `<AdaptiveText>` on Android.
 *
 * Called from C++ via fbjni — see `android/src/main/jni/AdaptiveTextMeasurer.cpp`.
 * Mirrors:
 *   * `AdaptiveTextTokenizer.kt` for tokenization, so the Yoga measure agrees
 *     with what `AdaptiveTextFlow` paints token-for-token.
 *   * `AdaptiveTextMeasurer.mm` (iOS) for the wrap algorithm, so the two
 *     platforms behave the same in resize tests.
 *
 * Per-token widths are produced by Compose's own `TextMeasurer` (seeded
 * from the application Context in `AdaptiveTextViewPackage`). Using the
 * same shaping engine that `AdaptiveTextFlow` renders with is what keeps
 * our Yoga height in lockstep with the actual painted layout — a
 * `Paint.measureText` baseline diverged by ~1 px per token from
 * Compose's `Paragraph`, which over a long paragraph was enough to make
 * the renderer wrap an extra line that our Yoga node never reserved
 * space for, showing up as clipped trailing lines after a style toggle.
 *
 * All sizes (fontSize, letterSpacing, …, maxWidth) are in *device pixels*.
 * The C++ side has already multiplied dp/sp values by Yoga's
 * `pointScaleFactor`; `density` and `fontScale` are passed through as
 * their own params so the Compose path can convert back to `TextUnit`
 * for `TextMeasurer`. We return `[width, height]` in pixels; Yoga
 * converts back to dp using the same scale factor.
 *
 * The class is `object` so the JNI side can resolve a single static method
 * once and cache it.
 */
@DoNotStrip
object AdaptiveTextNativeMeasurer {

  private val PUNCTUATION_ATTACH_CHARS = setOf(
    ',', '.', '!', '?', ';', ':', ')', ']', '}', '\'', '"',
  )

  private const val SPLIT_BY_WORD = 0
  private const val SPLIT_BY_GRAPHEME = 1

  private const val FONT_STYLE_NORMAL = 0
  private const val FONT_STYLE_ITALIC = 1

  // Process-wide token-width cache shared across every Yoga measure pass.
  //
  // Outer key — string fingerprint of every prop that affects glyph advance:
  //   `family|sizePx|weight|italic|letterSpacingPx|density|fontScale`.
  //   Anything that would change a token's measured width must appear here.
  // Inner key — the token text itself.
  // Value     — `[widthPx, heightPx]`, plus a per-font-key intrinsic line
  //   height shared by every entry under that key (font metrics depend on
  //   the font config, not the token text).
  //
  // Why this is the right shape for our hot path:
  //
  //   * On a resize drag, JS pushes a new container width every touch
  //     frame. Yoga calls `measureContent` → C++ `adaptive_text::measure`
  //     → JNI → this `measure(...)` for each frame. The text and font
  //     don't change between drag frames — only `maxWidthPx` does —
  //     so the per-token widths are identical. Caching by (font, token)
  //     collapses N `TextMeasurer.measure()` calls per frame into N
  //     hashmap lookups after the first measure.
  //
  //   * The same cache amortizes across multiple `<AdaptiveText>` views
  //     that share words (e.g. a list of headlines using one body font).
  //
  // Concurrency: Yoga measure callbacks can run on a Fabric worker
  // thread; we synchronize map mutations on `cacheLock`. The actual
  // `TextMeasurer.measure()` call runs outside the lock so other threads
  // measuring different tokens aren't blocked on font work.
  //
  // `intrinsicLineHeight` is the *max* per-token height we've seen for
  // this font config. Some glyph clusters (e.g. accented Latin like
  // "Spärck", or tokens that pull in emoji-fallback metrics) ship with a
  // taller intrinsic line box than plain ASCII tokens. If we seeded
  // `intrinsicLineHeight` from just the first measured token (as a
  // previous revision did), a paragraph whose first cached token was
  // ASCII but whose later tokens were taller would underestimate height
  // on the *fast path* — the cached entry's `intrinsicLineHeight` would
  // never catch up to the taller per-token heights stored in `widths`.
  // Tracking the max keeps the fast path's per-line height calculation
  // consistent with the slow path's.
  private class FontCacheEntry(
    intrinsicLineHeight: Float,
    val widths: HashMap<String, FloatArray> = HashMap(),
  ) {
    @Volatile var intrinsicLineHeight: Float = intrinsicLineHeight
  }
  private val widthCache = HashMap<String, FontCacheEntry>()
  private val cacheLock = Any()

  // Compose's text-measuring path that `AdaptiveTextFlow` renders with.
  // We deliberately use the same engine here so the Yoga measurer agrees
  // with the renderer down to the pixel: `Paint.measureText` and
  // Compose's `Paragraph` shaper can disagree by ~1 px per token, which
  // accumulates over a long paragraph into an extra wrapped line on the
  // renderer side that our Yoga node never reserved space for —
  // manifesting as the bottom line(s) being clipped inside Fabric's
  // strict-EXACTLY measure spec for AdaptiveTextView. The resolver is
  // seeded once from the application Context via `initialize(...)` from
  // `AdaptiveTextViewPackage`; if (for any reason) `initialize` was
  // never called, we fall back to the legacy Paint code path so we
  // never crash a measure pass.
  @Volatile private var fontFamilyResolver: FontFamily.Resolver? = null

  // We also reuse a single TextMeasurer instance keyed by the current
  // density+fontScale of the layout pass. Constructing a TextMeasurer is
  // cheap, but the resolver lookup behind it is not; one cached instance
  // amortizes per-call setup across every token in a paragraph.
  private var cachedDensityKey: Long = 0L
  private var cachedTextMeasurer: TextMeasurer? = null

  /**
   * Wires Compose's `FontFamilyResolver` from a real Android `Context`,
   * which is required for `TextMeasurer` to find platform fonts. Called
   * once from `AdaptiveTextViewPackage.createViewManagers` (the earliest
   * point where we have an ApplicationContext) before any Yoga measure
   * pass would have a reason to invoke us.
   *
   * Idempotent — repeated calls reuse the first resolver to keep the
   * font-style cache that backs it warm across re-registers (which can
   * happen on RN Fast Refresh).
   *
   * When we transition from "no resolver" → "resolver wired", we drop
   * any cached token widths. Reason: an earlier `measure(...)` call that
   * raced ahead of this `initialize` would have fallen back to the
   * `Paint`-based code path and cached those widths. `Paint.measureText`
   * and Compose's `Paragraph` shaper disagree by ~1 px per token, and
   * we *will* render with Compose, so leaving Paint widths in the cache
   * would cause exactly the off-by-one wrap bug between Yoga and the
   * Compose renderer we built `AdaptiveFlowLayout` to avoid.
   */
  @JvmStatic
  fun initialize(context: Context) {
    if (fontFamilyResolver == null) {
      fontFamilyResolver = createFontFamilyResolver(context)
      synchronized(cacheLock) {
        widthCache.clear()
        cachedTextMeasurer = null
        cachedDensityKey = 0L
      }
    }
  }

  /** Test/debug hook to drop all cached widths. Not exposed to JS today. */
  @JvmStatic
  @DoNotStrip
  fun clearCache() {
    synchronized(cacheLock) {
      widthCache.clear()
      cachedTextMeasurer = null
      cachedDensityKey = 0L
    }
  }

  /**
   * Single entry point invoked from C++. The JNI bridge passes primitives
   * only; the return value is a `FloatArray` of length 2 (`[widthPx,
   * heightPx]`).
   *
   * Per-token widths are measured through Compose's `TextMeasurer` so
   * we agree with what `AdaptiveTextFlow` actually paints. See the
   * `fontFamilyResolver` comment for the why; the short version is
   * `Paint.measureText` can disagree with Compose's shaper by enough
   * pixels per token to wrap an extra line on the renderer side, which
   * shows up as the bottom line(s) being clipped inside the
   * Fabric-controlled AdaptiveTextView bounds. If the resolver was
   * never seeded (e.g. an unusual host-app integration that skipped
   * `AdaptiveTextViewPackage.createViewManagers`), we transparently
   * fall back to a Paint-based measure path so we still produce a
   * sensible answer instead of crashing.
   */
  @JvmStatic
  @DoNotStrip
  fun measure(
    text: String,
    splitBy: Int,
    fontSizePx: Float,
    fontFamily: String,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
    lineHeightPx: Float,
    wordSpacingPx: Float,
    lineSpacingPx: Float,
    maxWidthPx: Float,
    density: Float,
    fontScale: Float,
  ): FloatArray {
    if (text.isEmpty()) {
      return floatArrayOf(0f, 0f)
    }

    val tokens = tokenize(text, splitBy)
    if (tokens.isEmpty()) {
      return floatArrayOf(0f, 0f)
    }

    val fontKey = fontCacheKey(
      fontFamily = fontFamily,
      fontSizePx = fontSizePx,
      fontWeight = fontWeight,
      fontStyle = fontStyle,
      letterSpacingPx = letterSpacingPx,
      density = density,
      fontScale = fontScale,
    )

    // Fast-path: every token's width is already cached. Skip TextMeasurer
    // / Paint allocation entirely. This is the common case during a drag.
    var fastPathMetrics: ArrayList<TokenSize>? = null
    var cachedIntrinsicLineHeight = 0f
    synchronized(cacheLock) {
      val entry = widthCache[fontKey]
      if (entry != null) {
        val out = ArrayList<TokenSize>(tokens.size)
        var allHit = true
        for (token in tokens) {
          val arr = entry.widths[token.text]
          if (arr == null) {
            allHit = false
            break
          }
          out.add(TokenSize(arr[0], arr[1]))
        }
        if (allHit) {
          fastPathMetrics = out
          cachedIntrinsicLineHeight = entry.intrinsicLineHeight
        }
      }
    }

    val metrics: List<TokenSize>
    val intrinsicLineHeight: Float
    val cachedFastPath = fastPathMetrics
    if (cachedFastPath != null) {
      metrics = cachedFastPath
      intrinsicLineHeight = cachedIntrinsicLineHeight
    } else {
      val measured = measureTokensFresh(
        tokens = tokens,
        fontKey = fontKey,
        fontSizePx = fontSizePx,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        letterSpacingPx = letterSpacingPx,
        lineHeightPx = lineHeightPx,
        density = density,
        fontScale = fontScale,
      )
      metrics = measured.metrics
      intrinsicLineHeight = measured.intrinsicLineHeight
    }

    val effectiveMaxWidth = if (maxWidthPx <= 0f) Float.POSITIVE_INFINITY else maxWidthPx
    val horizontalSpacing = if (wordSpacingPx > 0f) wordSpacingPx else 6f
    val verticalSpacing = if (lineSpacingPx > 0f) lineSpacingPx else 4f

    // Wrap *slightly* earlier than the renderer would. Compose's `Paragraph`
    // shaper (used by `Text` inside `AdaptiveFlowLayout`) and Compose's
    // `TextMeasurer` (used here) both go through `MultiParagraph` and
    // should produce identical per-token widths, but in practice they go
    // through subtly different intrinsics paths (`ParagraphIntrinsics`
    // for `Text` vs `MultiParagraphIntrinsics` for `TextMeasurer`) that
    // can disagree by < 1 px per token after subpixel rounding. Over a
    // long paragraph of 20–30 tokens that compounds into ~10–20 px of
    // accumulated drift, which is enough for the renderer to wrap one
    // extra trailing line that our measurer never reserved height for —
    // Fabric's strict EXACTLY measure spec on `AdaptiveTextView` then
    // clips that line out of the visible bounds.
    //
    // Shaving 6 dp off the wrap threshold biases us toward wrapping at
    // the same point or one token earlier than the renderer for the
    // longest realistic lines this library is asked to lay out (~40
    // tokens on a tablet column). The trade-off is at most ~6 dp of
    // empty trailing whitespace on lines that *exactly* hit the
    // original max width — invisible in practice, and far preferable to
    // a clipped trailing line. The clamp at the bottom of this function
    // still returns the real `widestLine`, so reported width isn't
    // shrunk.
    //
    // We previously used 3 dp; that turned out to be marginal on devices
    // with fractional density (2.625, 2.75) for paragraphs of comfortable
    // body text inside a ScrollView/FlatList row after a fontSize toggle,
    // which is what motivated bumping the budget.
    val safetyMarginPx = if (effectiveMaxWidth.isFinite()) {
      ceil(6f * (if (density > 0f && density.isFinite()) density else 1f))
    } else {
      0f
    }
    val wrapMaxWidth = if (effectiveMaxWidth.isFinite()) {
      (effectiveMaxWidth - safetyMarginPx).coerceAtLeast(1f)
    } else {
      effectiveMaxWidth
    }

    val lines = layOutLines(tokens, metrics, wrapMaxWidth, horizontalSpacing)

    var totalHeight = 0f
    var widestLine = 0f
    for ((index, line) in lines.withIndex()) {
      // Each rendered line must accommodate both the requested lineHeight
      // *and* the font's intrinsic ascent+descent. Compose's Text composable
      // clamps line height up to ascent+descent if `lineHeight` is shorter
      // (so glyphs aren't sliced), and a `LineHeightStyle(Trim.None)` line
      // also retains its full leading. Underestimating either side here
      // would let the renderer paint outside our reported height, which
      // Fabric strict-clips to AdaptiveTextView's bounds.
      val baseLine = max(line.height, intrinsicLineHeight)
      val h = if (lineHeightPx > 0f) max(lineHeightPx, baseLine) else baseLine
      if (index > 0) totalHeight += verticalSpacing
      totalHeight += h
      widestLine = max(widestLine, line.width)
    }

    val width = if (effectiveMaxWidth.isFinite()) {
      kotlin.math.min(widestLine, effectiveMaxWidth)
    } else {
      widestLine
    }

    return floatArrayOf(ceil(width), ceil(totalHeight))
  }

  private data class TokenMeasurements(
    val metrics: List<TokenSize>,
    val intrinsicLineHeight: Float,
  )

  /**
   * Cold-path measurement. Prefer Compose's `TextMeasurer` so the widths
   * we hand to Yoga line up with what `AdaptiveTextFlow` will paint;
   * fall back to `android.graphics.Paint` only if the resolver wasn't
   * seeded (which would mean the host app skipped our package init).
   * Either way, every token's measured `(width, height)` is written
   * into `widthCache` keyed by `fontKey` so the next call short-circuits
   * through the fast-path.
   */
  private fun measureTokensFresh(
    tokens: List<Token>,
    fontKey: String,
    fontSizePx: Float,
    fontFamily: String,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
    lineHeightPx: Float,
    density: Float,
    fontScale: Float,
  ): TokenMeasurements {
    val resolver = fontFamilyResolver
    return if (resolver != null) {
      measureTokensWithCompose(
        tokens = tokens,
        fontKey = fontKey,
        fontSizePx = fontSizePx,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        letterSpacingPx = letterSpacingPx,
        lineHeightPx = lineHeightPx,
        density = density,
        fontScale = fontScale,
        resolver = resolver,
      )
    } else {
      measureTokensWithPaint(
        tokens = tokens,
        fontKey = fontKey,
        fontSizePx = fontSizePx,
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        letterSpacingPx = letterSpacingPx,
      )
    }
  }

  /**
   * Compose-side token measurement. We construct a `TextStyle` that
   * mirrors what `AdaptiveTextFlow` paints (including the same
   * `LineHeightStyle.Trim.None` / `includeFontPadding = false` settings
   * we set on the renderer) so per-token widths and ascent+descent are
   * byte-identical to what the renderer will see.
   *
   * The C++ side pre-multiplied font/letter/line sizes into device px;
   * we convert them back to `TextUnit` via the layout pass's
   * `Density(density, fontScale)` so `TextMeasurer` operates on the
   * same `sp` values React Native and Compose would have passed it
   * organically.
   */
  private fun measureTokensWithCompose(
    tokens: List<Token>,
    fontKey: String,
    fontSizePx: Float,
    fontFamily: String,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
    lineHeightPx: Float,
    density: Float,
    fontScale: Float,
    resolver: FontFamily.Resolver,
  ): TokenMeasurements {
    val safeDensity = if (density > 0f && density.isFinite()) density else 1f
    val safeFontScale = if (fontScale > 0f && fontScale.isFinite()) fontScale else 1f
    val composeDensity = Density(density = safeDensity, fontScale = safeFontScale)

    val measurer = obtainTextMeasurer(composeDensity, resolver, safeDensity, safeFontScale)

    val fontSizeSp = pxToTextUnit(fontSizePx, safeDensity, safeFontScale, composeDensity)
    val lineHeightSp = if (lineHeightPx > 0f) {
      pxToTextUnit(lineHeightPx, safeDensity, safeFontScale, composeDensity)
    } else {
      TextUnit.Unspecified
    }
    val letterSpacingSp = if (letterSpacingPx != 0f) {
      pxToTextUnit(letterSpacingPx, safeDensity, safeFontScale, composeDensity)
    } else {
      TextUnit.Unspecified
    }

    val style = TextStyle(
      fontSize = fontSizeSp,
      fontWeight = ComposeFontWeight(parseFontWeight(fontWeight)),
      fontStyle = if (fontStyle == FONT_STYLE_ITALIC) {
        ComposeFontStyle.Italic
      } else {
        ComposeFontStyle.Normal
      },
      fontFamily = if (fontFamily.isNotEmpty()) FontFamily.Default else null,
      letterSpacing = letterSpacingSp,
      lineHeight = lineHeightSp,
      lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
      ),
      platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

    // Single-line, unconstrained width: this returns each token's
    // natural-width as Compose's `Paragraph` shapes it, which is what
    // FlowRow will use when deciding wrap on the render side.
    val emptyConstraints = Constraints()

    var maxIntrinsic = 0f
    val out = ArrayList<TokenSize>(tokens.size)
    for (token in tokens) {
      var hit: TokenSize? = null
      synchronized(cacheLock) {
        val entry = widthCache[fontKey]
        val arr = entry?.widths?.get(token.text)
        if (arr != null) {
          hit = TokenSize(arr[0], arr[1])
          maxIntrinsic = max(maxIntrinsic, entry.intrinsicLineHeight)
        }
      }
      val cached = hit
      if (cached != null) {
        out.add(cached)
        continue
      }
      val layout = measurer.measure(
        text = AnnotatedString(token.text),
        style = style,
        maxLines = 1,
        softWrap = false,
        constraints = emptyConstraints,
        layoutDirection = LayoutDirection.Ltr,
        density = composeDensity,
      )
      val width = layout.size.width.toFloat()
      val height = layout.size.height.toFloat()
      val size = TokenSize(width, height)
      maxIntrinsic = max(maxIntrinsic, height)
      synchronized(cacheLock) {
        val entry = widthCache.getOrPut(fontKey) {
          FontCacheEntry(intrinsicLineHeight = height)
        }
        entry.widths[token.text] = floatArrayOf(width, height)
        // Keep the entry's shared `intrinsicLineHeight` in lockstep with
        // the tallest per-token height we've seen for this font config.
        // The fast-path reads this value when it serves cached metrics
        // and uses it as a floor when summing per-line heights; if it
        // ever lags behind the per-token heights we already cached, the
        // measurer would underestimate height for paragraphs whose
        // tallest tokens haven't been observed yet on the slow path.
        if (height > entry.intrinsicLineHeight) {
          entry.intrinsicLineHeight = height
        }
      }
      out.add(size)
    }

    return TokenMeasurements(metrics = out, intrinsicLineHeight = maxIntrinsic)
  }

  /**
   * Legacy Paint-based measurement. Kept as a fallback for the rare
   * case where `initialize(...)` wasn't called (e.g. a host app that
   * loads the JNI before our `ReactPackage` is registered). Same wrap
   * algorithm as the Compose path; just different shaper.
   */
  private fun measureTokensWithPaint(
    tokens: List<Token>,
    fontKey: String,
    fontSizePx: Float,
    fontFamily: String,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
  ): TokenMeasurements {
    val paint = buildPaint(
      fontSizePx = fontSizePx,
      fontFamily = fontFamily,
      fontWeight = fontWeight,
      fontStyle = fontStyle,
      letterSpacingPx = letterSpacingPx,
    )
    val fm = paint.fontMetrics
    val intrinsicLineHeight = fm.descent - fm.ascent

    val out = ArrayList<TokenSize>(tokens.size)
    for (token in tokens) {
      var hit: TokenSize? = null
      synchronized(cacheLock) {
        val entry = widthCache[fontKey]
        val arr = entry?.widths?.get(token.text)
        if (arr != null) {
          hit = TokenSize(arr[0], arr[1])
        }
      }
      val cached = hit
      if (cached != null) {
        out.add(cached)
        continue
      }
      val measured = measureToken(paint, token.text)
      synchronized(cacheLock) {
        val entry = widthCache.getOrPut(fontKey) {
          FontCacheEntry(intrinsicLineHeight = intrinsicLineHeight)
        }
        entry.widths[token.text] = floatArrayOf(measured.width, measured.height)
        if (measured.height > entry.intrinsicLineHeight) {
          entry.intrinsicLineHeight = measured.height
        }
      }
      out.add(measured)
    }
    return TokenMeasurements(metrics = out, intrinsicLineHeight = intrinsicLineHeight)
  }

  /** Reuse one `TextMeasurer` across tokens at the same density+fontScale. */
  private fun obtainTextMeasurer(
    composeDensity: Density,
    resolver: FontFamily.Resolver,
    densityKey: Float,
    fontScaleKey: Float,
  ): TextMeasurer {
    val key = densityKey.toRawBits().toLong().shl(32) or
      fontScaleKey.toRawBits().toLong().and(0xFFFFFFFFL)
    var measurer = cachedTextMeasurer
    if (measurer != null && cachedDensityKey == key) {
      return measurer
    }
    measurer = TextMeasurer(
      defaultFontFamilyResolver = resolver,
      defaultDensity = composeDensity,
      defaultLayoutDirection = LayoutDirection.Ltr,
    )
    cachedTextMeasurer = measurer
    cachedDensityKey = key
    return measurer
  }

  /**
   * Inverse of `Density.toPx()` for the px values C++ already pre-baked
   * with this same density+fontScale: we recover the original `TextUnit`
   * so Compose's `TextMeasurer` applies density+fontScale exactly once
   * (inside its own measure pass) rather than twice (here *and* there).
   *
   * `Density.toSp(Float)` divides by `density * fontScale`, which is the
   * exact transform C++ applied going the other way, so we round-trip
   * back to the original `sp` value the user passed via JS.
   */
  private fun pxToTextUnit(
    px: Float,
    density: Float,
    fontScale: Float,
    composeDensity: Density,
  ): TextUnit {
    if (!px.isFinite() || density <= 0f || fontScale <= 0f) {
      return TextUnit.Unspecified
    }
    return with(composeDensity) { px.toSp() }
  }

  // Visibility relaxed from `private` to `internal` so the JVM unit
  // test in `android/src/test/.../AdaptiveTextNativeMeasurerCacheKeyTest.kt`
  // can directly call this builder and verify that every prop affecting
  // glyph advance contributes a distinct key (AGENTS.md §8.A: any new
  // width-affecting prop must appear in this key, otherwise stale
  // cached widths produce wrap mis-prediction).
  internal fun fontCacheKey(
    fontFamily: String,
    fontSizePx: Float,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
    density: Float,
    fontScale: Float,
  ): String = buildString(64) {
    append(fontFamily)
    append('|')
    append(fontSizePx)
    append('|')
    append(fontWeight)
    append('|')
    append(if (fontStyle == FONT_STYLE_ITALIC) '1' else '0')
    append('|')
    append(letterSpacingPx)
    append('|')
    append(density)
    append('|')
    append(fontScale)
  }

  // MARK: - Tokenization

  private data class Token(val text: String, val attachToPrevious: Boolean)

  private data class TokenSize(val width: Float, val height: Float)

  private data class Line(val width: Float, val height: Float)

  private fun tokenize(text: String, mode: Int): List<Token> = when (mode) {
    SPLIT_BY_GRAPHEME -> tokenizeGraphemes(text)
    SPLIT_BY_WORD -> tokenizeWords(text)
    else -> tokenizeWords(text)
  }

  private fun tokenizeWords(text: String): List<Token> {
    if (text.isEmpty()) return emptyList()
    val pieces = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    val out = ArrayList<Token>(pieces.size)
    for (piece in pieces) {
      val attach = piece.all { it in PUNCTUATION_ATTACH_CHARS } && out.isNotEmpty()
      out.add(Token(piece, attach))
    }
    return out
  }

  private fun tokenizeGraphemes(text: String): List<Token> {
    val out = ArrayList<Token>()
    val iter = BreakIterator.getCharacterInstance(Locale.getDefault())
    iter.setText(text)
    var start = iter.first()
    var end = iter.next()
    while (end != BreakIterator.DONE) {
      val cluster = text.substring(start, end)
      if (cluster.isNotBlank()) {
        out.add(Token(cluster, false))
      }
      start = end
      end = iter.next()
    }
    return out
  }

  // MARK: - Paint setup

  private fun buildPaint(
    fontSizePx: Float,
    fontFamily: String,
    fontWeight: String,
    fontStyle: Int,
    letterSpacingPx: Float,
  ): Paint {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.textSize = if (fontSizePx > 0f) fontSizePx else 17f

    val typeface = resolveTypeface(fontFamily, fontWeight, fontStyle)
    paint.typeface = typeface

    if (letterSpacingPx != 0f && paint.textSize > 0f) {
      // Paint.letterSpacing is in *em* units. We store px on the C++ side for
      // unit consistency with iOS, then convert back here.
      paint.letterSpacing = letterSpacingPx / paint.textSize
    }
    return paint
  }

  private fun resolveTypeface(family: String, weight: String, fontStyle: Int): Typeface {
    val base = if (family.isNotEmpty()) {
      Typeface.create(family, Typeface.NORMAL)
    } else {
      Typeface.DEFAULT
    }

    val w = parseFontWeight(weight)
    val isBold = w >= 600
    val italic = fontStyle == FONT_STYLE_ITALIC

    return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
      Typeface.create(base, w, italic)
    } else {
      val style = when {
        isBold && italic -> Typeface.BOLD_ITALIC
        isBold -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
      }
      Typeface.create(base, style)
    }
  }

  /** Numeric/named weight string (matches the iOS / Compose mappings) -> a 1..1000 number. */
  private fun parseFontWeight(raw: String): Int = when (raw) {
    "100", "ultraLight" -> 100
    "200", "thin" -> 200
    "300", "light" -> 300
    "500", "medium" -> 500
    "600", "semibold" -> 600
    "700", "bold" -> 700
    "800", "heavy" -> 800
    "900", "black" -> 900
    "400", "regular", "normal", "" -> 400
    else -> 400
  }

  // MARK: - Measurement

  private fun measureToken(paint: Paint, text: String): TokenSize {
    if (text.isEmpty()) return TokenSize(0f, 0f)
    val width = paint.measureText(text)
    val fm = paint.fontMetrics
    val height = fm.descent - fm.ascent
    return TokenSize(ceil(width), ceil(height))
  }

  /**
   * Mirror of `AdaptiveTextFlowLayout.layOutLines` (Swift) and `LayOutLines`
   * (`AdaptiveTextMeasurer.mm`). Wrap rule:
   *   * a token whose right edge would exceed `maxWidth` starts a new line,
   *   * unless it's the first token on the current line, OR
   *   * unless it's "attached" to the previous token (e.g. trailing
   *     punctuation glued onto the prior word).
   */
  private fun layOutLines(
    tokens: List<Token>,
    metrics: List<TokenSize>,
    maxWidth: Float,
    horizontalSpacing: Float,
  ): List<Line> {
    val out = ArrayList<MutableLine>()
    out.add(MutableLine())

    for (i in tokens.indices) {
      val m = metrics[i]
      val attach = tokens[i].attachToPrevious
      val current = out.last()
      val isFirstOnLine = current.entries == 0
      val leadingSpace = if (isFirstOnLine || attach) 0f else horizontalSpacing
      val candidateRight = current.width + leadingSpace + m.width

      if (candidateRight > maxWidth && !isFirstOnLine && !attach) {
        out.add(MutableLine())
      }

      val line = out.last()
      val firstNow = line.entries == 0
      val usedLeading = if (firstNow || attach) 0f else horizontalSpacing
      val x = line.width + usedLeading
      line.width = x + m.width
      line.height = max(line.height, m.height)
      line.entries += 1
    }

    return out.map { Line(width = it.width, height = it.height) }
  }

  private class MutableLine(
    var width: Float = 0f,
    var height: Float = 0f,
    var entries: Int = 0,
  )
}

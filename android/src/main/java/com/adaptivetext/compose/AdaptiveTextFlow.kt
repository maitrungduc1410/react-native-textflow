package com.adaptivetext.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.approachLayout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import kotlinx.coroutines.launch

/**
 * Renders [props].text as a flowing collection of per-token Material 3
 * `Text` views whose **positions** animate when the layout reflows.
 *
 * The motion is driven by a custom `Modifier.animateTokenPlacement` (see
 * below) that sits inside a `LookaheadScope` and interpolates each
 * surviving token's `(x, y)` between two layout passes. Crucially we do
 * **not** use `Modifier.animateBounds` here — that modifier animates both
 * size and position, which causes adjacent tokens in `FlowRow` to overlap
 * mid-animation and overdraw each other's trailing glyphs (the "missing
 * trailing letter" bug). Animating placement only keeps each `Text` at
 * its natural intrinsic width at all times, so siblings are always placed
 * against the natural widths and can never overlap.
 *
 * Text style otherwise matches what RN's built-in `<Text>` would render —
 * all glyph metrics come from `props.fontFamily`/`fontSize`/`lineHeight`.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AdaptiveTextFlow(props: AdaptiveTextRenderProps) {
  val tokens = remember(props.text, props.splitMode) {
    AdaptiveTextTokenizer.tokenize(props.text, props.splitMode)
  }

  val horizontalSpacing = (if (props.wordSpacing > 0) props.wordSpacing else 6f).dp
  val verticalSpacing = (if (props.lineSpacing > 0) props.lineSpacing else 4f).dp

  val resolvedColor = if (props.textColor == Color.Unspecified) {
    LocalContentColor.current
  } else {
    props.textColor
  }

  val textStyle = TextStyle(
    color = resolvedColor,
    fontSize = props.fontSize,
    fontWeight = props.fontWeight,
    fontStyle = props.fontStyle,
    fontFamily = props.fontFamily?.let { FontFamily.Default },
    letterSpacing = props.letterSpacing,
    lineHeight = props.lineHeight,
    // Pin the per-line height to *exactly* `lineHeight`, with the glyph
    // centered inside that box and no platform font padding above/below.
    // `Trim.None` keeps the leading inside the line box (so a single
    // `Text` is `lineHeight` tall, matching what
    // `AdaptiveTextNativeMeasurer` reports up to Yoga), and
    // `includeFontPadding = false` removes the legacy Android-only
    // extra spacing TextView used to add above the ascent / below the
    // descent — that padding is what historically caused the Compose
    // renderer to paint a slightly taller box than our measurer
    // expected, and Fabric's strict-EXACTLY measure spec on
    // AdaptiveTextView clipped the overflow.
    lineHeightStyle = LineHeightStyle(
      alignment = LineHeightStyle.Alignment.Center,
      trim = LineHeightStyle.Trim.None,
    ),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
  )

  val offsetSpec: FiniteAnimationSpec<IntOffset> =
    props.animation.toOffsetSpec() ?: snap()
  val animateMotion = props.animation !is AdaptiveAnimation.None

  // Stable mask of "this token attaches to the prior token" flags, in the
  // same order tokens are emitted into AdaptiveFlowLayout below. The
  // custom layout uses this to keep trailing punctuation glued to its
  // word (never wrap "word" and "," to different lines).
  val attachMask = remember(tokens) { tokens.map { it.attachToPrevious } }

  LookaheadScope {
    val scope = this
    // We use our own `AdaptiveFlowLayout` instead of Compose's
    // `FlowRow` so the wrap algorithm here matches the Yoga measurer's
    // (`AdaptiveTextNativeMeasurer.layOutLines`) byte-for-byte. See the
    // `AdaptiveFlowLayout` KDoc for the failure mode that motivated this
    // — short version: `FlowRow`'s integer-rounded spacing and
    // `Int`-only wrap math diverge from our float-based measurer just
    // enough on fractional-density devices to lose a trailing line of
    // text to clipping under Fabric's strict-EXACTLY measure spec.
    val flowAlignment = props.textAlign.toFlowAlignment(LocalLayoutDirection.current)
    AdaptiveFlowLayout(
      horizontalSpacing = horizontalSpacing,
      verticalSpacing = verticalSpacing,
      attachMask = attachMask,
      alignment = flowAlignment,
      modifier = Modifier
        .fillMaxWidth()
        .wrapContentHeight()
        .semantics { contentDescription = props.text },
    ) {
      tokens.forEach { token ->
        // `key(token.id)` ties each `Text`'s identity (and its placement-
        // animation state) to the token's stable id, not its position in
        // the layout's slot list. Without this, when token text changes
        // at the same slot (e.g. card expand swaps SHORT for LONG), the
        // surviving slot would replay its old animation state and the
        // intra-token glyph layout would briefly disagree with what
        // `AdaptiveFlowLayout` is placing. Identity-keyed slots give
        // surviving tokens smooth reflow and let new/removed tokens
        // come in cleanly.
        key(token.id) {
          val placementModifier = if (animateMotion) {
            Modifier.animateTokenPlacement(scope, offsetSpec)
          } else {
            Modifier
          }
          // `softWrap = false` + `maxLines = 1` + `overflow = Visible`
          // mirrors iOS's `.lineLimit(1).fixedSize(horizontal: true, ...)`
          // pattern: each token always measures and renders at its
          // natural single-line width regardless of any parent constraint
          // that might be in flight during a re-layout. Combined with
          // placement-only animation, this guarantees the visible glyphs
          // never get clipped or shrunk while a reflow is animating.
          Text(
            text = token.text,
            style = textStyle,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = placementModifier.semantics { invisibleToUser() },
          )
        }
      }
    }
  }
}

/**
 * Animates only the **position** of this element when its parent
 * re-layouts. Size is never animated — the child always measures and
 * places at its lookahead (target) size.
 *
 * Why placement-only instead of `Modifier.animateBounds`:
 *
 *   * `animateBounds` interpolates both bounds (size + position) and
 *     reports the *interpolated* size to the parent layout while still
 *     drawing the child at its natural content. Inside a `FlowRow` of
 *     adjacent `Text` tokens, that's a disaster: token N's slot is
 *     temporarily, say, 30dp wide while the natural Text is 50dp,
 *     FlowRow places token N+1 starting at slot-N.right (= 30dp + spacing),
 *     and token N+1's glyphs overdraw the trailing glyphs of token N.
 *     The user sees "expand" rendered as "expan" — the trailing "d" is
 *     literally painted over by token N+1.
 *
 *   * Placement-only animation sidesteps the problem by never lying about
 *     the size. Each `Text` always reports its natural intrinsic width;
 *     FlowRow always places siblings against natural widths; siblings
 *     never overlap. The "every word springs to its new line" effect is
 *     preserved by the placement animation alone.
 *
 * Implementation is the standard Lookahead "animate placement" pattern.
 * The element's target absolute position inside the `LookaheadScope` is
 * recorded after each layout pass; an `Animatable<IntOffset>` chases the
 * latest target, and we place the child at `(animatedAbsolute - currentAbsolute)`
 * to deliver the "previously here, ending up here" interpolation.
 */
@OptIn(ExperimentalComposeUiApi::class)
private fun Modifier.animateTokenPlacement(
  lookaheadScope: LookaheadScope,
  spec: FiniteAnimationSpec<IntOffset>,
): Modifier = composed {
  val coroutineScope = rememberCoroutineScope()
  val offsetAnim = remember {
    Animatable(IntOffset.Zero, IntOffset.VectorConverter)
  }
  var lastTarget: IntOffset? by remember { mutableStateOf(null) }

  this.approachLayout(
    // Size never animates — children always report and use their
    // natural intrinsic width. See the modifier docstring for the
    // sibling-overlap reason this matters.
    isMeasurementApproachInProgress = { false },
    // The approach lambda needs to re-run on every frame while
    // `offsetAnim` is interpolating so the per-frame placement reads
    // the latest animated value. Without this flag returning true,
    // Compose runs the approach pass exactly once per lookahead change
    // and then short-circuits to the lookahead value — meaning the
    // visible position would snap to the target instead of animating.
    isPlacementApproachInProgress = { offsetAnim.isRunning },
  ) { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
      val coords = coordinates
      if (coords == null) {
        placeable.place(0, 0)
        return@layout
      }
      with(lookaheadScope) {
        val target = lookaheadScopeCoordinates
          .localLookaheadPositionOf(coords)
          .round()
        val previousTarget = lastTarget
        if (previousTarget == null) {
          // First placement of this token under this identity — seed the
          // animatable at the target so the very first paint is exact
          // and there's no spurious "fly in" animation.
          lastTarget = target
          coroutineScope.launch { offsetAnim.snapTo(target) }
        } else if (previousTarget != target) {
          lastTarget = target
          coroutineScope.launch { offsetAnim.animateTo(target, spec) }
        }
        // Compose places this child at the position that the current
        // (approach) layout would naturally give it. We want it at the
        // *animated* absolute position instead. Subtract the natural
        // approach position so the local `place(...)` offset moves the
        // child to the animated absolute spot.
        val animatedAbsolute = offsetAnim.value
        val currentAbsolute = lookaheadScopeCoordinates
          .localPositionOf(coords, Offset.Zero)
          .round()
        placeable.place(animatedAbsolute - currentAbsolute)
      }
    }
  }
}

package com.adaptivetext.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Horizontal "wrap a fraction earlier than the raw max-width" budget the
 * renderer applies to itself. Kept in lockstep with the constant baked into
 * `AdaptiveTextNativeMeasurer.measure` so the Yoga measurer and the
 * renderer wrap at *exactly* the same effective max-width.
 *
 * Without this, Compose's `Text` (via `Paragraph` intrinsics) and Compose's
 * `TextMeasurer` (via `MultiParagraph` intrinsics) can disagree by < 1 px
 * per token after subpixel rounding. On a long paragraph that compounds
 * into the renderer wrapping one extra trailing line that the measurer
 * never reserved height for, and Fabric's strict EXACTLY measure spec on
 * `AdaptiveTextView` then clips that line out of view.
 *
 * Trading away ~6 dp of trailing whitespace per line — invisible in
 * practice — guarantees the measurer's and the renderer's wrap pass agree
 * on line count for the longest realistic paragraphs this library is asked
 * to lay out.
 */
private val WRAP_SAFETY_MARGIN = 6.dp

/**
 * Horizontal alignment of each laid-out line *within* the flow's measured
 * width. Mirrors SwiftUI's `HorizontalAlignment` on the iOS side, and gives
 * `AdaptiveText` `textAlign` parity across platforms.
 *
 * Values are pre-resolved against the current `LayoutDirection` by the call
 * site (`AdaptiveTextAlign.toFlowAlignment`) so this layout never has to
 * know about RTL — it just offsets x by the value below.
 */
enum class AdaptiveFlowAlignment { START, CENTER, END }

/**
 * Custom flow `Layout` that mirrors `AdaptiveTextNativeMeasurer.layOutLines`
 * on the Yoga side and `AdaptiveTextFlowLayout` (SwiftUI) on iOS.
 *
 * Why we don't use Compose's built-in `FlowRow`:
 *
 * `FlowRow` and our Yoga measurer agree on each token's natural width
 * (both call into Compose's `Paragraph` shaper through `TextMeasurer`),
 * but their wrap *algorithms* differ in subtle ways that can flip the
 * line-break decision for one token at the very end of a paragraph:
 *
 *   * `FlowRow` rounds inter-token spacing via `ceil(spacing.dp.toPx()).toInt()`.
 *     On devices with a fractional density (e.g. 2.625, 2.75) that shifts
 *     spacing up by < 1 px per gap relative to our measurer's raw
 *     `spacing * density` float. Accumulated over a long line this is
 *     enough to push one trailing token onto a new line in `FlowRow`
 *     while our Yoga measurer thought it fit on the previous one.
 *
 *   * `FlowRow` uses `Int` math throughout its wrap loop; our measurer
 *     uses `Float`. Same per-step difference applies.
 *
 *   * `FlowRow` measures each child against `Constraints(maxWidth =
 *     mainAxisMax)`. Compose's `Paragraph` then clamps `layoutSize.width`
 *     to that max if (theoretically) the natural width exceeds it. We
 *     pass `Constraints.Infinity` in the measurer, so a pathological
 *     single-token-wider-than-the-line case would also diverge.
 *
 * The user-visible symptom of any of those is the bottom line(s) of a
 * paragraph being clipped: Fabric pre-sizes `AdaptiveTextView` to the
 * measurer's height, `FlowRow` wraps to one more line than the measurer
 * predicted, and that line falls outside the view's strict-EXACTLY
 * bounds.
 *
 * Fixing each `FlowRow` quirk inside our measurer is fragile (the same
 * paragraph could regress again when Compose tweaks its rounding). The
 * robust shape — and the one we already use on iOS — is to own *both*
 * the measurement-time and render-time wrap algorithms ourselves, so
 * the two are byte-identical by construction. Per-token widths still
 * come from Compose's shaper (`measurable.measure(...)` here, the same
 * `TextMeasurer` over in the Yoga side), so the rendered glyphs and
 * the measurer's reserved width agree.
 *
 * @param horizontalSpacing space between two adjacent tokens on the same
 *   line. Mirrors the `wordSpacing` prop on the JS side. Passed as `Dp`
 *   and converted inside the measure block so we can choose the exact
 *   same `toPx()` rounding as `AdaptiveTextNativeMeasurer` (raw float;
 *   no `ceil`).
 * @param verticalSpacing space between consecutive lines. Mirrors
 *   `lineSpacing` on the JS side.
 * @param attachMask `attachMask[i] = true` means token `i` is glued to
 *   token `i-1` — wrap will never separate the two, mirroring the
 *   "trailing punctuation stays with the prior word" rule in
 *   `AdaptiveTextTokenizer`.
 * @param alignment horizontal alignment of each laid-out line within
 *   the flow's measured width. Resolved against `LayoutDirection` by
 *   the call site so this `Layout` doesn't have to know about RTL.
 */
@Composable
fun AdaptiveFlowLayout(
  horizontalSpacing: Dp,
  verticalSpacing: Dp,
  attachMask: List<Boolean>,
  modifier: Modifier = Modifier,
  alignment: AdaptiveFlowAlignment = AdaptiveFlowAlignment.START,
  content: @Composable () -> Unit,
) {
  val layoutDirection = LocalLayoutDirection.current
  Layout(
    modifier = modifier,
    content = content,
  ) { measurables, constraints ->
    val horizontalSpacingPx = horizontalSpacing.toPx()
    val verticalSpacingPx = verticalSpacing.toPx()
    val safetyMarginPx = if (constraints.hasBoundedWidth) {
      ceil(WRAP_SAFETY_MARGIN.toPx())
    } else {
      0f
    }
    val effectiveMaxWidth = if (constraints.hasBoundedWidth) {
      (constraints.maxWidth.toFloat() - safetyMarginPx).coerceAtLeast(1f)
    } else {
      Float.POSITIVE_INFINITY
    }

    // Each token is single-line (`softWrap = false`, `maxLines = 1`),
    // and we want every child's *natural* width regardless of the
    // FlowLayout's own bounds — we want the shaper to tell us how wide
    // the glyphs actually are so our wrap pass can decide on them. We
    // pass `Constraints.Infinity` on the main axis for that reason.
    // Cross-axis is unconstrained for the same reason: a token's full
    // line-box height determines the row height it lives in.
    val childConstraints = Constraints(
      minWidth = 0,
      maxWidth = Constraints.Infinity,
      minHeight = 0,
      maxHeight = Constraints.Infinity,
    )
    val placeables = measurables.map { it.measure(childConstraints) }

    val lines = layOutLines(placeables, attachMask, effectiveMaxWidth, horizontalSpacingPx)

    var totalHeight = 0f
    var widestLine = 0f
    for ((index, line) in lines.withIndex()) {
      if (index > 0) totalHeight += verticalSpacingPx
      totalHeight += line.height
      widestLine = max(widestLine, line.width)
    }

    // Clamp to the *outer* max-width here, not the safety-shrunk
    // `effectiveMaxWidth`. The safety margin only biases the *wrap pass*
    // toward an extra line break — it shouldn't shrink the layout box
    // we report up to ComposeView, otherwise lists of cards would
    // visibly under-fill their cell width by ~6 dp.
    val totalWidth = if (constraints.hasBoundedWidth) {
      min(widestLine, constraints.maxWidth.toFloat())
    } else {
      widestLine
    }

    // `ceil` (not `toInt`) so we never under-allocate the layout box and
    // hide a fractional sliver of a line at the bottom. Matches what the
    // Yoga measurer does with `ceil(totalHeight)` / `ceil(width)` so the
    // two sides report the same integer size to their respective
    // parents (Fabric on the measurer side, the ComposeView host here).
    val layoutWidth = ceil(totalWidth).toInt().coerceIn(
      constraints.minWidth,
      if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE,
    )
    val layoutHeight = ceil(totalHeight).toInt().coerceIn(
      constraints.minHeight,
      if (constraints.hasBoundedHeight) constraints.maxHeight else Int.MAX_VALUE,
    )

    layout(layoutWidth, layoutHeight) {
      var y = 0f
      for (line in lines) {
        // Per-line x offset so the line starts at the correct horizontal
        // edge of the layout box. Mirrors `AdaptiveTextFlowLayout` on iOS:
        //
        //   * START / END flip with the active layout direction so an LTR
        //     `start` aligns to the left edge and an RTL `start` aligns to
        //     the right edge — same semantics as RN's `textAlign: 'start'`.
        //   * LEFT / RIGHT are absolute and ignore layout direction.
        //   * CENTER centers the line in the layout box.
        //
        // We compute the offset in pixels and add it to each entry's
        // intra-line x (which is already 0 → line.width laid out left-to-
        // right by `layOutLines`). For RTL `start`/`end`, the per-line
        // x within the line stays left-to-right because each token is a
        // single short word; the *line* is what gets pushed to the right
        // edge of the box. That's the same approach SwiftUI's flow takes
        // on iOS for RTL Arabic/Hebrew sample paragraphs and matches what
        // users expect of `textAlign` for short-token flows.
        val widthF = layoutWidth.toFloat()
        val lineXOffset = when (alignment) {
          AdaptiveFlowAlignment.START ->
            if (layoutDirection == LayoutDirection.Rtl) widthF - line.width else 0f
          AdaptiveFlowAlignment.END ->
            if (layoutDirection == LayoutDirection.Rtl) 0f else widthF - line.width
          AdaptiveFlowAlignment.CENTER ->
            (widthF - line.width) / 2f
        }
        for (entry in line.entries) {
          // `place` (not `placeRelative`) because we've already resolved
          // RTL into `lineXOffset` above. Using `placeRelative` would
          // double-mirror the x position in RTL.
          entry.placeable.place((lineXOffset + entry.x).toInt(), y.toInt())
        }
        y += line.height + verticalSpacingPx
      }
    }
  }
}

private class FlowLayoutLine {
  val entries: MutableList<FlowLayoutEntry> = mutableListOf()
  var width: Float = 0f
  var height: Float = 0f
}

private class FlowLayoutEntry(val placeable: Placeable, val x: Float)

/**
 * Wrap algorithm — kept byte-identical to `AdaptiveTextNativeMeasurer
 * .layOutLines` so the Yoga measurer's predicted line count and this
 * `Layout`'s actual placed line count always agree.
 *
 *   * A token whose right edge would exceed `maxWidth` starts a new line,
 *   * unless it's the first token on the current line (we never wrap an
 *     empty line — we'd just paint that token over the right edge), OR
 *   * unless `attachMask[i]` says it's glued to the previous token
 *     (trailing punctuation must not be orphaned to a new line).
 */
private fun layOutLines(
  placeables: List<Placeable>,
  attachMask: List<Boolean>,
  maxWidth: Float,
  horizontalSpacing: Float,
): List<FlowLayoutLine> {
  val lines = mutableListOf(FlowLayoutLine())

  for (i in placeables.indices) {
    val p = placeables[i]
    val pw = p.width.toFloat()
    val ph = p.height.toFloat()
    val attach = i < attachMask.size && attachMask[i]

    var current = lines.last()
    val isFirstOnLine = current.entries.isEmpty()
    val leadingSpace = if (isFirstOnLine || attach) 0f else horizontalSpacing
    val candidateRight = current.width + leadingSpace + pw

    if (candidateRight > maxWidth && !isFirstOnLine && !attach) {
      lines.add(FlowLayoutLine())
      current = lines.last()
    }

    val firstNow = current.entries.isEmpty()
    val usedLeading = if (firstNow || attach) 0f else horizontalSpacing
    val x = current.width + usedLeading
    current.entries.add(FlowLayoutEntry(p, x))
    current.width = x + pw
    current.height = max(current.height, ph)
  }

  return lines
}

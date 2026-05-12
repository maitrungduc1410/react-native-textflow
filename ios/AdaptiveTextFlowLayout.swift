import SwiftUI

/// Flow layout that places its children left-to-right, wrapping when there
/// is no more horizontal space.
///
/// SwiftUI animates between two layout passes by interpolating each
/// subview's position **as long as the subview keeps its identity across
/// the passes**. Our parent uses `ForEach(_, id: \.id)` over stable token
/// IDs, so every word that survives a re-layout slides smoothly to its new
/// position. New tokens fade in via the per-subview transition; removed
/// tokens fade out.
///
/// Available iOS 16+. Older systems are unsupported by this library.
@available(iOS 16.0, *)
struct AdaptiveTextFlowLayout: Layout {
  var horizontalSpacing: CGFloat
  var verticalSpacing: CGFloat
  /// User-supplied per-line height (in points). When non-zero, every line
  /// reserves *at least* `lineHeight` of vertical space — matching what the
  /// Yoga measurer reports via `max(props.lineHeight, naturalLineHeight)`.
  ///
  /// Without this floor the renderer advances `y` by each token's natural
  /// `sizeThatFits` height (~20 pt for SF Pro 18 with `.leading(.tight)`),
  /// which is *smaller* than the measurer's `max(lineHeight, natural)` when
  /// the user supplies a `lineHeight` larger than the natural font height
  /// (e.g. `fontSize: 18`, `lineHeight: 26`). Yoga then sizes
  /// `AdaptiveTextView` to the measurer's number, but the rendered text
  /// only fills the renderer's smaller number — the difference shows up as
  /// trailing empty space inside the parent frame.
  ///
  /// Honoring `lineHeight` here makes the iOS pipeline behave like
  /// `AdaptiveTextFlow` on Android (where Compose's `Text` natively
  /// expands each line to `style.lineHeight` via `LineHeightStyle`).
  ///
  /// `max(line.height, lineHeight)` is the right shape because when the
  /// user-supplied `lineHeight` is *smaller* than natural (e.g. the Style
  /// Morph case: `fontSize: 33`, `lineHeight: 28`), SwiftUI's `Text` still
  /// paints at natural height; floor-at-natural keeps the renderer's row
  /// advance correct in that direction too.
  var lineHeight: CGFloat
  var alignment: HorizontalAlignment
  var attachMask: [Bool]

  func sizeThatFits(
    proposal: ProposedViewSize,
    subviews: Subviews,
    cache _: inout Cache
  ) -> CGSize {
    let maxWidth = proposal.width ?? .infinity
    let lines = layOutLines(maxWidth: maxWidth, subviews: subviews)
    let totalHeight = lines.reduce(CGFloat(0)) { acc, line in
      acc + (acc > 0 ? verticalSpacing : 0) + slotHeight(for: line)
    }
    let widestLine = lines.map(\.width).max() ?? 0
    return CGSize(width: min(widestLine, maxWidth.isFinite ? maxWidth : widestLine), height: totalHeight)
  }

  func placeSubviews(
    in bounds: CGRect,
    proposal _: ProposedViewSize,
    subviews: Subviews,
    cache _: inout Cache
  ) {
    let lines = layOutLines(maxWidth: bounds.width, subviews: subviews)
    var y = bounds.minY
    for line in lines {
      let xOffset: CGFloat
      switch alignment {
      case .center:
        xOffset = bounds.minX + (bounds.width - line.width) / 2
      case .trailing:
        xOffset = bounds.minX + bounds.width - line.width
      default:
        xOffset = bounds.minX
      }
      // Vertically center each token inside the (potentially taller) slot
      // so a `lineHeight: 40` row with a 20 pt natural glyph box doesn't
      // collapse its glyphs to the top of the row. Mirrors
      // `LineHeightStyle.Alignment.Center` used by AdaptiveTextFlow on
      // Android.
      let slot = slotHeight(for: line)
      let glyphOffset = max(0, (slot - line.height) / 2)
      for entry in line.entries {
        let position = CGPoint(x: xOffset + entry.x, y: y + glyphOffset)
        entry.subview.place(
          at: position,
          anchor: .topLeading,
          proposal: ProposedViewSize(width: entry.size.width, height: entry.size.height)
        )
      }
      y += slot + verticalSpacing
    }
  }

  /// Per-line vertical slot used by both `sizeThatFits` and `placeSubviews`.
  /// Must stay in lockstep with `AdaptiveTextMeasurer.mm`'s height pass:
  ///   `h = props.lineHeight > 0 ? max(props.lineHeight, line.height) : line.height`
  /// so what we report up to SwiftUI's parent (and ultimately Yoga, via the
  /// hosting view's intrinsic content size) matches what we actually paint.
  private func slotHeight(for line: Line) -> CGFloat {
    lineHeight > 0 ? max(line.height, lineHeight) : line.height
  }

  // MARK: - Internal layout pass

  struct Cache {}
  func makeCache(subviews _: Subviews) -> Cache { Cache() }

  private struct Entry {
    let subview: LayoutSubviews.Element
    let size: CGSize
    var x: CGFloat
  }

  private struct Line {
    var entries: [Entry] = []
    var width: CGFloat = 0
    var height: CGFloat = 0
  }

  private func layOutLines(maxWidth: CGFloat, subviews: Subviews) -> [Line] {
    var lines: [Line] = [Line()]
    for (index, subview) in subviews.enumerated() {
      let attach = index < attachMask.count ? attachMask[index] : false
      let size = subview.sizeThatFits(.unspecified)
      let isFirstOnLine = lines[lines.count - 1].entries.isEmpty
      let leadingSpace = isFirstOnLine || attach ? 0 : horizontalSpacing
      let candidateRight = lines[lines.count - 1].width + leadingSpace + size.width

      // Wrap to a new line when this token would overflow, but never wrap if
      // the token is glued to its predecessor (attachToPrevious).
      if candidateRight > maxWidth, !isFirstOnLine, !attach {
        lines.append(Line())
      }

      let lineIndex = lines.count - 1
      let isFirstNow = lines[lineIndex].entries.isEmpty
      let usedLeading = isFirstNow || attach ? 0 : horizontalSpacing
      let x = lines[lineIndex].width + usedLeading
      lines[lineIndex].entries.append(Entry(subview: subview, size: size, x: x))
      lines[lineIndex].width = x + size.width
      lines[lineIndex].height = max(lines[lineIndex].height, size.height)
    }
    return lines
  }
}

import SwiftUI

/// Root SwiftUI view backing `<AdaptiveText>` on iOS.
///
/// Layout and motion live entirely on the SwiftUI side. React Native
/// pushes new prop snapshots into `model`, this view re-runs `body`, and
/// SwiftUI handles the smooth interpolation between the previous and new
/// layouts because each token keeps a stable identity via `ForEach(id:)`.
@available(iOS 16.0, *)
struct AdaptiveTextContent: View {
  @ObservedObject var model: AdaptiveTextPropsModel
  @Environment(\.layoutDirection) private var layoutDirection

  var body: some View {
    let p = model.props
    let tokens = AdaptiveTextTokenizer.tokenize(p.text, mode: p.splitMode)
    let attachMask = tokens.map(\.attachToPrevious)
    let animation = p.animation.swiftUIAnimation()

    GeometryReader { geometry in
      let containerWidth = geometry.size.width

      AdaptiveTextFlowLayout(
        horizontalSpacing: p.wordSpacing > 0 ? p.wordSpacing : 6,
        verticalSpacing: p.lineSpacing > 0 ? p.lineSpacing : 4,
        // Forward `lineHeight` so the renderer's per-line slot matches the
        // Yoga measurer's reported per-line height. The measurer floors at
        // `max(props.lineHeight, naturalLineHeight)` and so must the
        // renderer, otherwise the parent frame is sized for the measurer's
        // taller number and the rendered text leaves trailing empty space
        // inside it (the user-reported bug in the Resizable Container
        // demo). See `AdaptiveTextFlowLayout.lineHeight` for the full story.
        lineHeight: p.lineHeight > 0 ? p.lineHeight : 0,
        alignment: p.textAlign.horizontalAlignment(in: layoutDirection),
        attachMask: attachMask
      ) {
        ForEach(tokens) { token in
          tokenView(text: token.text, props: p)
            .transition(
              .asymmetric(
                insertion: .opacity.combined(with: .scale(scale: 0.85)),
                removal: .opacity
              )
            )
        }
      }
      .frame(width: containerWidth, alignment: alignmentValue(p.textAlign))
      .animation(animation, value: tokens)
      .animation(animation, value: containerWidth)
      .animation(animation, value: p.fontSize)
      .animation(animation, value: p.letterSpacing)
      .animation(animation, value: p.lineHeight)
      .animation(animation, value: p.wordSpacing)
      .animation(animation, value: p.lineSpacing)
      // Animate alignment changes too: when JS flips `textAlign` from e.g.
      // `start` → `center`, every token's x position shifts. Without an
      // animation modifier observing `textAlign`, SwiftUI applies the new
      // layout instantly. Listing it here parks the change in the same
      // implicit animation as the other layout-affecting props, so tokens
      // glide to the new alignment instead of snapping — matching the
      // `Modifier.animateTokenPlacement` behavior on Android, which
      // animates *any* placement diff regardless of cause.
      .animation(animation, value: p.textAlign)
    }
    .accessibilityElement(children: .ignore)
    .accessibilityLabel(p.text)
  }

  // MARK: - Per-token view

  @ViewBuilder
  private func tokenView(text: String, props p: AdaptiveTextRenderProps) -> some View {
    Text(text)
      .font(font(for: p))
      .italic(p.italic)
      .kerning(p.letterSpacing)
      .foregroundColor(Color(p.textColor))
      .lineLimit(1)
      .fixedSize(horizontal: true, vertical: true)
  }

  /// Resolves the SwiftUI `Font` for the current props, honouring Dynamic
  /// Type via the `relativeTo:` overload of `.system` / `.custom`.
  private func font(for p: AdaptiveTextRenderProps) -> Font {
    let size = p.fontSize > 0 ? p.fontSize : 17
    if let family = p.fontFamily, !family.isEmpty {
      return Font.custom(family, size: size, relativeTo: .body)
    }
    return Font.system(size: size, weight: p.fontWeight, design: .default)
      .leading(.tight)
  }

  private func alignmentValue(_ a: AdaptiveTextAlign) -> Alignment {
    switch a {
    case .left: return .topLeading
    case .right: return .topTrailing
    case .center: return .top
    case .start, .auto:
      return layoutDirection == .rightToLeft ? .topTrailing : .topLeading
    case .end:
      return layoutDirection == .rightToLeft ? .topLeading : .topTrailing
    }
  }
}

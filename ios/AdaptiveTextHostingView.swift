import SwiftUI
import UIKit

/// `UIView` shell that owns a `UIHostingController` rendering the SwiftUI
/// flow. Codegen-driven props arrive via the single `apply(...)` method
/// and are funnelled into a published model that drives the SwiftUI
/// re-render.
@available(iOS 16.0, *)
@objc(AdaptiveTextHostingView)
public final class AdaptiveTextHostingView: UIView {
  private let model: AdaptiveTextPropsModel
  private let hostingController: UIHostingController<AdaptiveTextContent>

  public override init(frame: CGRect) {
    let model = AdaptiveTextPropsModel()
    let host = UIHostingController(rootView: AdaptiveTextContent(model: model))
    self.model = model
    self.hostingController = host
    super.init(frame: frame)

    host.view.backgroundColor = .clear
    host.view.translatesAutoresizingMaskIntoConstraints = false
    addSubview(host.view)
    NSLayoutConstraint.activate([
      host.view.leadingAnchor.constraint(equalTo: leadingAnchor),
      host.view.trailingAnchor.constraint(equalTo: trailingAnchor),
      host.view.topAnchor.constraint(equalTo: topAnchor),
      host.view.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])

    // Keep the view a11y-quiet at the UIView layer; the SwiftUI tree
    // composes its own accessibilityLabel from the rendered text.
    isAccessibilityElement = false
    accessibilityElements = [host.view as Any]
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) is not supported on AdaptiveTextHostingView")
  }

  /// Resets the model back to defaults. Called by the Fabric `prepareForRecycle` hook.
  @objc public func reset() {
    model.props = AdaptiveTextRenderProps()
  }

  /// Single funnel for prop updates. ObjC++ converts every codegen prop
  /// to a primitive (NSString / CGFloat / UIColor) and calls this once
  /// per `updateProps` cycle.
  @objc public func applyText(
    _ text: NSString,
    splitBy: NSString,
    fontSize: CGFloat,
    fontFamily: NSString?,
    fontWeight: NSString,
    fontStyle: NSString,
    textColor: UIColor?,
    letterSpacing: CGFloat,
    lineHeight: CGFloat,
    textAlign: NSString,
    wordSpacing: CGFloat,
    lineSpacing: CGFloat,
    animationType: NSString,
    damping: CGFloat,
    stiffness: CGFloat,
    mass: CGFloat,
    duration: CGFloat,
    easing: NSString
  ) {
    var next = AdaptiveTextRenderProps()
    next.text = text as String
    next.splitMode = (splitBy as String) == "grapheme" ? .grapheme : .word
    next.fontSize = fontSize > 0 ? fontSize : 17
    next.fontFamily = (fontFamily as String?).flatMap { $0.isEmpty ? nil : $0 }
    next.fontWeight = parseFontWeight(fontWeight as String)
    next.italic = (fontStyle as String) == "italic"
    next.textColor = textColor ?? .label
    next.letterSpacing = letterSpacing
    next.lineHeight = lineHeight
    next.textAlign = parseTextAlign(textAlign as String)
    next.wordSpacing = wordSpacing
    next.lineSpacing = lineSpacing
    next.animation = parseAnimation(
      type: animationType as String,
      damping: damping,
      stiffness: stiffness,
      mass: mass,
      duration: duration,
      easing: easing as String
    )

    if next != model.props {
      model.props = next
    }
  }

  // MARK: - String parsing helpers

  private func parseFontWeight(_ raw: String) -> Font.Weight {
    switch raw {
    case "100", "ultraLight": return .ultraLight
    case "200", "thin": return .thin
    case "300", "light": return .light
    case "400", "regular", "normal", "": return .regular
    case "500", "medium": return .medium
    case "600", "semibold": return .semibold
    case "700", "bold": return .bold
    case "800", "heavy": return .heavy
    case "900", "black": return .black
    default: return .regular
    }
  }

  private func parseTextAlign(_ raw: String) -> AdaptiveTextAlign {
    switch raw {
    case "left": return .left
    case "right": return .right
    case "center": return .center
    case "end": return .end
    case "auto": return .auto
    default: return .start
    }
  }

  private func parseAnimation(
    type: String,
    damping: CGFloat,
    stiffness: CGFloat,
    mass: CGFloat,
    duration: CGFloat,
    easing: String
  ) -> AdaptiveTextAnimation {
    switch type {
    case "none":
      return .none
    case "timing":
      let e: AdaptiveTextEasing
      switch easing {
      case "linear": e = .linear
      case "easeIn": e = .easeIn
      case "easeOut": e = .easeOut
      default: e = .easeInOut
      }
      return .timing(duration: TimeInterval(duration), easing: e)
    default:
      return .spring(damping: damping, stiffness: stiffness, mass: mass)
    }
  }
}


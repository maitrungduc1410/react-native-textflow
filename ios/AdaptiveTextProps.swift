import SwiftUI
import UIKit

/// Snapshot of every prop the JS side has forwarded for one render. Wrapping
/// every field in a single value-typed struct lets the SwiftUI tree observe
/// "props changed" with a single `@Published` event instead of one per
/// individual style key, which keeps animations from being triggered twice
/// on a single React render.
struct AdaptiveTextRenderProps: Equatable {
  var text: String = ""
  var splitMode: AdaptiveTextSplitMode = .word
  var fontSize: CGFloat = 17
  var fontFamily: String?
  var fontWeight: Font.Weight = .regular
  var italic: Bool = false
  var textColor: UIColor = .label
  var letterSpacing: CGFloat = 0
  var lineHeight: CGFloat = 0
  var textAlign: AdaptiveTextAlign = .start
  var wordSpacing: CGFloat = 6
  var lineSpacing: CGFloat = 4
  var animation: AdaptiveTextAnimation = .defaultSpring
}

enum AdaptiveTextSplitMode: Equatable {
  case word
  case grapheme
}

enum AdaptiveTextAlign: Equatable {
  case auto
  case left
  case right
  case center
  case start
  case end

  /// SwiftUI horizontal alignment to use inside the flow layout. `.auto`
  /// follows the layout direction (handled by SwiftUI's `.layoutDirection`).
  func horizontalAlignment(in layoutDirection: LayoutDirection) -> HorizontalAlignment {
    switch self {
    case .left: return .leading
    case .right: return .trailing
    case .center: return .center
    case .start, .auto:
      return layoutDirection == .rightToLeft ? .trailing : .leading
    case .end:
      return layoutDirection == .rightToLeft ? .leading : .trailing
    }
  }
}

enum AdaptiveTextAnimation: Equatable {
  case spring(damping: CGFloat, stiffness: CGFloat, mass: CGFloat)
  case timing(duration: TimeInterval, easing: AdaptiveTextEasing)
  case none

  static let defaultSpring: AdaptiveTextAnimation = .spring(
    damping: 18,
    stiffness: 220,
    mass: 1
  )

  /// SwiftUI `Animation` representation of this config, or `nil` if motion
  /// should be disabled.
  func swiftUIAnimation() -> Animation? {
    switch self {
    case .spring(let damping, let stiffness, let mass):
      // Convert the iOS-friendly damping/stiffness/mass triple into
      // SwiftUI's `interpolatingSpring`. This keeps the animation
      // physically grounded and matches Compose's `spring` semantics.
      let d = damping > 0 ? damping : 18
      let s = stiffness > 0 ? stiffness : 220
      let m = mass > 0 ? mass : 1
      return .interpolatingSpring(mass: m, stiffness: s, damping: d, initialVelocity: 0)
    case .timing(let duration, let easing):
      let d = duration > 0 ? duration / 1000.0 : 0.25
      switch easing {
      case .linear: return .linear(duration: d)
      case .easeIn: return .easeIn(duration: d)
      case .easeOut: return .easeOut(duration: d)
      case .easeInOut: return .easeInOut(duration: d)
      }
    case .none:
      return nil
    }
  }
}

enum AdaptiveTextEasing: Equatable {
  case linear
  case easeIn
  case easeOut
  case easeInOut
}

/// Internal published model passed into the SwiftUI tree.
final class AdaptiveTextPropsModel: ObservableObject {
  @Published var props: AdaptiveTextRenderProps = AdaptiveTextRenderProps()
}

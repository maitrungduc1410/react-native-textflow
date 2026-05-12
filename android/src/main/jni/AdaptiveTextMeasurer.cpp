/**
 * Android implementation of `adaptive_text::measure`.
 *
 * Yoga only sees a measure callback in C++, but Android's text engine lives
 * in Java (`android.graphics.Paint`). To avoid recreating font metrics in
 * C++ — which would never match the on-screen Compose rendering — this file
 * delegates to a Kotlin object via fbjni. The Kotlin side uses Paint with
 * the same density / typeface the Compose `AdaptiveTextFlow` composable
 * will eventually use, then runs the same word-wrap algorithm we use on
 * iOS in `AdaptiveTextMeasurer.mm` and in the SwiftUI flow layout.
 *
 * The fbjni handle is resolved lazily on the first measure call. The static
 * Java method is cached for the life of the process; subsequent calls just
 * marshal primitive props and dispatch.
 */

#include <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>

#include <fbjni/fbjni.h>

#include <algorithm>
#include <cmath>
#include <limits>

using AdaptiveTextViewProps = facebook::react::AdaptiveTextViewProps;
using AdaptiveTextViewSplitBy = facebook::react::AdaptiveTextViewSplitBy;
using AdaptiveTextViewFontStyle = facebook::react::AdaptiveTextViewFontStyle;
using LayoutConstraints = facebook::react::LayoutConstraints;
using LayoutContext = facebook::react::LayoutContext;

namespace adaptive_text {

namespace {

namespace jni = facebook::jni;

/// Mirrors `com/adaptivetext/measurer/AdaptiveTextNativeMeasurer.measure`.
///
/// All sizes (`fontSize`, `letterSpacing`, `lineHeight`, `wordSpacing`,
/// `lineSpacing`, `maxWidth`) are passed in *device pixels*. Callers (this
/// file) do the dp-or-sp -> px conversion using the `pointScaleFactor`
/// Yoga gives us via `LayoutConstraints`, so the Kotlin side never has to
/// know about Android's display density.
struct AdaptiveTextNativeMeasurer
    : jni::JavaClass<AdaptiveTextNativeMeasurer> {
  static constexpr auto kJavaDescriptor =
      "Lcom/adaptivetext/measurer/AdaptiveTextNativeMeasurer;";

  /// Returns a Java `float[]` of length 2: `[widthPx, heightPx]`.
  ///
  /// We pass `density` (= Yoga's `pointScaleFactor`) and `fontScale`
  /// (= Yoga's `fontSizeMultiplier`) as their own params so the Kotlin
  /// side can build a Compose `Density(density, fontScale)` and drive
  /// `TextMeasurer` with values consistent with what `AdaptiveTextFlow`'s
  /// renderer will see — the C++ side has already pre-multiplied
  /// font/letter/line sizes into pixels, but Compose's `TextMeasurer`
  /// works in `TextUnit` (sp), so the Kotlin side converts px back to
  /// sp through this density.
  static jni::local_ref<jni::JArrayFloat> measure(
      const std::string &text,
      jint splitBy,
      jfloat fontSizePx,
      const std::string &fontFamily,
      const std::string &fontWeight,
      jint fontStyle,
      jfloat letterSpacingPx,
      jfloat lineHeightPx,
      jfloat wordSpacingPx,
      jfloat lineSpacingPx,
      jfloat maxWidthPx,
      jfloat density,
      jfloat fontScale) {
    static auto cls = javaClassStatic();
    static auto method = cls->getStaticMethod<jni::JArrayFloat(
        std::string,
        jint,
        jfloat,
        std::string,
        std::string,
        jint,
        jfloat,
        jfloat,
        jfloat,
        jfloat,
        jfloat,
        jfloat,
        jfloat)>("measure");

    return method(
        cls,
        text,
        splitBy,
        fontSizePx,
        fontFamily,
        fontWeight,
        fontStyle,
        letterSpacingPx,
        lineHeightPx,
        wordSpacingPx,
        lineSpacingPx,
        maxWidthPx,
        density,
        fontScale);
  }
};

constexpr jint kSplitByWord = 0;
constexpr jint kSplitByGrapheme = 1;
constexpr jint kFontStyleNormal = 0;
constexpr jint kFontStyleItalic = 1;

jint splitByToInt(AdaptiveTextViewSplitBy mode) {
  switch (mode) {
    case AdaptiveTextViewSplitBy::Grapheme:
      return kSplitByGrapheme;
    case AdaptiveTextViewSplitBy::Word:
    default:
      return kSplitByWord;
  }
}

jint fontStyleToInt(AdaptiveTextViewFontStyle style) {
  switch (style) {
    case AdaptiveTextViewFontStyle::Italic:
      return kFontStyleItalic;
    case AdaptiveTextViewFontStyle::Normal:
    default:
      return kFontStyleNormal;
  }
}

} // namespace

facebook::react::Size measure(
    const AdaptiveTextViewProps &props,
    const LayoutContext &context,
    const LayoutConstraints &constraints) {
  // Yoga / Fabric on Android operate in *logical points* (dp/sp): the JNI
  // binding (`SurfaceHandlerBinding::constraintLayout`) divides incoming
  // pixel values by `pointScaleFactor` before handing them to the C++ layer.
  // Props (`fontSize`, `letterSpacing`, …) likewise come from JS in points.
  //
  // `Paint` on the Java side wants *physical pixels*. We multiply going in,
  // and divide the returned width/height before handing back to Yoga so the
  // Size we return stays in points (which is what `measureContent` is
  // expected to produce).
  jfloat scale = context.pointScaleFactor;
  if (!std::isfinite(scale) || scale <= 0) {
    scale = 1.0f;
  }

  // `fontSizeMultiplier` reflects the user's "Display size" / accessibility
  // text-scale preference — same semantics as iOS Dynamic Type. Compose
  // applies this through its `Density(fontScale = …)` automatically; we
  // need to mirror it manually for the measurer to stay in lockstep.
  jfloat fontScale = context.fontSizeMultiplier;
  if (!std::isfinite(fontScale) || fontScale <= 0) {
    fontScale = 1.0f;
  }

  jfloat maxWidthDIP = constraints.maximumSize.width;
  jfloat maxWidthPx = std::isfinite(maxWidthDIP)
      ? maxWidthDIP * scale
      : -1.0f; // sentinel for "no constraint"

  jfloat fontSizePx = (props.fontSize > 0 ? props.fontSize : 17.0f) * fontScale * scale;
  jfloat letterSpacingPx = props.letterSpacing * scale;
  jfloat lineHeightPx = props.lineHeight * fontScale * scale;
  jfloat wordSpacingPx = (props.wordSpacing > 0 ? props.wordSpacing : 6.0f) * scale;
  jfloat lineSpacingPx = (props.lineSpacing > 0 ? props.lineSpacing : 4.0f) * scale;

  try {
    auto result = AdaptiveTextNativeMeasurer::measure(
        props.text,
        splitByToInt(props.splitBy),
        fontSizePx,
        props.fontFamily,
        props.fontWeight,
        fontStyleToInt(props.fontStyle),
        letterSpacingPx,
        lineHeightPx,
        wordSpacingPx,
        lineSpacingPx,
        maxWidthPx,
        scale,
        fontScale);

    if (result == nullptr || result->size() < 2) {
      return facebook::react::Size{0, 0};
    }
    auto values = result->getRegion(0, 2);

    // Clamp into Yoga's `[minimumSize, maximumSize]` constraint box. The
    // Kotlin measurer returns the *content's* intrinsic size (e.g. 326 dp
    // for a paragraph whose longest line is 326 dp wide), but Yoga may
    // have asked us for an EXACTLY-sized box (`minimumSize.width ==
    // maximumSize.width`). Returning a value outside the proposed range
    // is logged as "returned an invalid measurement" by
    // `YogaLayoutableShadowNode` on every measure pass even though Yoga
    // tolerates and clamps the value internally. Honor the contract.
    facebook::react::Float width =
        static_cast<facebook::react::Float>(values[0] / scale);
    facebook::react::Float height =
        static_cast<facebook::react::Float>(values[1] / scale);

    if (std::isfinite(constraints.minimumSize.width)) {
      width = std::max(width, constraints.minimumSize.width);
    }
    if (std::isfinite(constraints.maximumSize.width)) {
      width = std::min(width, constraints.maximumSize.width);
    }
    if (std::isfinite(constraints.minimumSize.height)) {
      height = std::max(height, constraints.minimumSize.height);
    }
    if (std::isfinite(constraints.maximumSize.height)) {
      height = std::min(height, constraints.maximumSize.height);
    }

    return facebook::react::Size{width, height};
  } catch (const std::exception &) {
    // Better to claim zero size than crash the JS thread; a follow-up
    // measure pass will retry once Compose / state catches up.
    return facebook::react::Size{0, 0};
  }
}

} // namespace adaptive_text

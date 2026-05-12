#pragma once

#ifdef __cplusplus

// `#include` (not `#import`) so this header builds cleanly on the Android JNI
// side, which compiles as pure C++ with `-Wpedantic -Werror`. iOS clang would
// accept `#import` but standardising on `#include` keeps the headers
// platform-agnostic.
#include <react/renderer/components/AdaptiveTextViewSpec/Props.h>
#include <react/renderer/core/LayoutConstraints.h>
#include <react/renderer/core/LayoutContext.h>
#include <react/renderer/graphics/Size.h>

namespace adaptive_text {

/// Measures the natural content size of an `<AdaptiveText>` for a given set
/// of props, the surface-level layout context (density / font scale) and
/// Yoga's layout constraints.
///
/// The implementation is platform-specific:
///   * iOS — `ios/AdaptiveTextMeasurer.mm` uses CoreText / `NSAttributedString`
///     and mirrors `AdaptiveTextFlowLayout.layOutLines` from the SwiftUI side.
///   * Android — `android/src/main/jni/AdaptiveTextMeasurer.cpp` bridges into
///     a Kotlin `AdaptiveTextNativeMeasurer` via fbjni, which uses
///     `android.graphics.Paint` and the same flow algorithm.
///
/// On both platforms the C++ caller (the shadow node) operates in *logical
/// points* (Yoga's coordinate space). The Android impl converts to physical
/// pixels via `context.pointScaleFactor` for Paint, then back. iOS passes
/// values straight through since UIKit / CoreText are already in points.
facebook::react::Size measure(
    const facebook::react::AdaptiveTextViewProps &props,
    const facebook::react::LayoutContext &context,
    const facebook::react::LayoutConstraints &constraints);

} // namespace adaptive_text

#endif

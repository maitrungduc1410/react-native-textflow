#pragma once

#ifdef __cplusplus

#include <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextShadowNode.h>
#include <react/renderer/core/ConcreteComponentDescriptor.h>

namespace facebook::react {

/// Component descriptor that pairs the codegen-generated props with our
/// custom `AdaptiveTextShadowNode` (which adds the Yoga measure callback).
///
///   * iOS — installed via `[AdaptiveTextView componentDescriptorProvider]`
///     in `AdaptiveTextView.mm`.
///   * Android — listed in `react-native.config.js` under
///     `componentDescriptors`, so RN's autolinking task emits
///     `concreteComponentDescriptorProvider<AdaptiveTextComponentDescriptor>()`
///     into the app's generated `autolinking.cpp`. The shadow header
///     `android/src/main/jni/AdaptiveTextViewSpec.h` then ensures this
///     symbol is in scope when that registration runs.
using AdaptiveTextComponentDescriptor =
    ConcreteComponentDescriptor<AdaptiveTextShadowNode>;

} // namespace facebook::react

#endif

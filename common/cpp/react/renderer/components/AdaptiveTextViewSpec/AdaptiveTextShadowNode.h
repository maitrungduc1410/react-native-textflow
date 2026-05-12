#pragma once

#ifdef __cplusplus

#include <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>
#include <react/renderer/components/AdaptiveTextViewSpec/ShadowNodes.h>
#include <react/renderer/core/LayoutContext.h>

namespace facebook::react {

/// Shadow node for `<AdaptiveText>` that participates in Yoga measurement.
///
/// We tag this node with `LeafYogaNode` + `MeasurableYogaNode` so Yoga calls
/// our `measureContent` during layout. The actual measurement is delegated
/// to a per-platform implementation of `adaptive_text::measure`, which
/// mirrors the on-screen flow algorithm exactly so the height Yoga sees
/// matches what SwiftUI / Compose will eventually paint.
///
/// Without this, parents that auto-size to their child (e.g. a `<View>`
/// with no fixed height wrapping `<AdaptiveText>`) collapse to zero content
/// height — even though the platform layer happily renders the text.
class AdaptiveTextShadowNode final : public AdaptiveTextViewShadowNode {
 public:
  using AdaptiveTextViewShadowNode::AdaptiveTextViewShadowNode;

  static ShadowNodeTraits BaseTraits() {
    auto traits = AdaptiveTextViewShadowNode::BaseTraits();
    traits.set(ShadowNodeTraits::Trait::LeafYogaNode);
    traits.set(ShadowNodeTraits::Trait::MeasurableYogaNode);
    return traits;
  }

  Size measureContent(
      const LayoutContext &layoutContext,
      const LayoutConstraints &layoutConstraints) const override {
    // Forward both the context (gives us `pointScaleFactor` /
    // `fontSizeMultiplier`) and the constraints. The Android measurer needs
    // the scale factor to convert to/from physical pixels for `Paint`.
    return adaptive_text::measure(
        getConcreteProps(), layoutContext, layoutConstraints);
  }
};

} // namespace facebook::react

#endif

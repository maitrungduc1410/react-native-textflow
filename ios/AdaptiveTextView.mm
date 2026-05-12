#import "AdaptiveTextView.h"

#import <React/RCTConversions.h>

#import <react/renderer/components/AdaptiveTextViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/AdaptiveTextViewSpec/Props.h>
#import <react/renderer/components/AdaptiveTextViewSpec/RCTComponentViewHelpers.h>

// Custom descriptor that pairs the codegen props with our shadow node + Yoga
// measurer. The header lives under `common/cpp/...` so iOS and Android share
// it; the iOS podspec adds that directory to the header search path.
#import <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextComponentDescriptor.h>
// CocoaPods auto-generates `<PodName>-Swift.h` from the pod's Swift sources
// so Obj-C++ can call back into Swift. The pod is named `TextFlow` (see
// `TextFlow.podspec`), so the generated bridging header is
// `TextFlow-Swift.h`. Renaming the pod required renaming this import too.
#import "TextFlow-Swift.h"
#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

// Translates the codegen-generated enums into NSStrings so the Swift side
// can stay framework-agnostic.

static NSString *_Nonnull stringForSplitBy(AdaptiveTextViewSplitBy value) {
  switch (value) {
    case AdaptiveTextViewSplitBy::Word: return @"word";
    case AdaptiveTextViewSplitBy::Grapheme: return @"grapheme";
  }
}

static NSString *_Nonnull stringForFontStyle(AdaptiveTextViewFontStyle value) {
  switch (value) {
    case AdaptiveTextViewFontStyle::Normal: return @"normal";
    case AdaptiveTextViewFontStyle::Italic: return @"italic";
  }
}

static NSString *_Nonnull stringForTextAlign(AdaptiveTextViewTextAlign value) {
  switch (value) {
    case AdaptiveTextViewTextAlign::Auto: return @"auto";
    case AdaptiveTextViewTextAlign::Left: return @"left";
    case AdaptiveTextViewTextAlign::Right: return @"right";
    case AdaptiveTextViewTextAlign::Center: return @"center";
    case AdaptiveTextViewTextAlign::Start: return @"start";
    case AdaptiveTextViewTextAlign::End: return @"end";
  }
}

static NSString *_Nonnull stringForAnimationType(AdaptiveTextViewType value) {
  switch (value) {
    case AdaptiveTextViewType::Spring: return @"spring";
    case AdaptiveTextViewType::Timing: return @"timing";
    case AdaptiveTextViewType::None: return @"none";
  }
}

static NSString *_Nonnull stringForEasing(AdaptiveTextViewEasing value) {
  switch (value) {
    case AdaptiveTextViewEasing::Linear: return @"linear";
    case AdaptiveTextViewEasing::EaseIn: return @"easeIn";
    case AdaptiveTextViewEasing::EaseOut: return @"easeOut";
    case AdaptiveTextViewEasing::EaseInOut: return @"easeInOut";
  }
}

@implementation AdaptiveTextView {
  AdaptiveTextHostingView *_hostingView;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  // Use our custom descriptor so the Yoga measure callback installed in
  // `AdaptiveTextShadowNode` runs during layout. The codegen descriptor would
  // otherwise leave the node sized only by its style props, which collapses
  // wrapping text containers to zero content height.
  return concreteComponentDescriptorProvider<AdaptiveTextComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const AdaptiveTextViewProps>();
    _props = defaultProps;

    _hostingView = [[AdaptiveTextHostingView alloc] initWithFrame:self.bounds];
    _hostingView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;

    self.contentView = _hostingView;
  }

  return self;
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
  const auto &newViewProps = *std::static_pointer_cast<AdaptiveTextViewProps const>(props);

  UIColor *textColor = nil;
  if (newViewProps.textColor) {
    textColor = RCTUIColorFromSharedColor(newViewProps.textColor);
  }

  NSString *fontFamily = nil;
  if (!newViewProps.fontFamily.empty()) {
    fontFamily = [NSString stringWithUTF8String:newViewProps.fontFamily.c_str()];
  }

  NSString *fontWeight = newViewProps.fontWeight.empty()
      ? @"normal"
      : [NSString stringWithUTF8String:newViewProps.fontWeight.c_str()];

  NSString *text = [NSString stringWithUTF8String:newViewProps.text.c_str()];

  [_hostingView applyText:text
                  splitBy:stringForSplitBy(newViewProps.splitBy)
                 fontSize:(CGFloat)newViewProps.fontSize
               fontFamily:fontFamily
               fontWeight:fontWeight
                fontStyle:stringForFontStyle(newViewProps.fontStyle)
                textColor:textColor
            letterSpacing:(CGFloat)newViewProps.letterSpacing
               lineHeight:(CGFloat)newViewProps.lineHeight
                textAlign:stringForTextAlign(newViewProps.textAlign)
              wordSpacing:(CGFloat)newViewProps.wordSpacing
              lineSpacing:(CGFloat)newViewProps.lineSpacing
            animationType:stringForAnimationType(newViewProps.animation.type)
                  damping:(CGFloat)newViewProps.animation.damping
                stiffness:(CGFloat)newViewProps.animation.stiffness
                     mass:(CGFloat)newViewProps.animation.mass
                 duration:(CGFloat)newViewProps.animation.duration
                   easing:stringForEasing(newViewProps.animation.easing)];

  [super updateProps:props oldProps:oldProps];
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  [_hostingView reset];
}

@end

#import <React/RCTViewComponentView.h>
#import <UIKit/UIKit.h>

#ifndef AdaptiveTextView_h
#define AdaptiveTextView_h

NS_ASSUME_NONNULL_BEGIN

/// Fabric component view for `<AdaptiveText>`.
///
/// Hosts a SwiftUI flow layout via `UIHostingController` and forwards codegen
/// props to a Swift-side observable model on every `updateProps` cycle.
@interface AdaptiveTextView : RCTViewComponentView
@end

NS_ASSUME_NONNULL_END

#endif /* AdaptiveTextView_h */

//
// AdaptiveTextMeasurerTests.mm
//
// XCTest suite covering the iOS Yoga measurer entry point
// (`adaptive_text::measure`) end-to-end. These guard the per-platform
// invariants in AGENTS.md §4.1 — the wrap algorithm, the line-height
// floor (§4.1.4), and constraints clamping (§4.1.5) — that the
// measurer must honor for SwiftUI's renderer to lay out without
// clipping or trailing whitespace.
//
// Why call the C++ entry point directly instead of mounting a SwiftUI
// view:
//   1. The .mm file IS the source of truth for Yoga's measure callback.
//      Mounting a view round-trips through Fabric mount + a
//      UIHostingController sizing pass, which adds noise to assertions
//      about pure measurer behavior.
//   2. The function is referentially transparent once tokenization runs:
//      same `(props, constraints)` produces the same `Size`. Easy to
//      assert exactly without timing or animation involved.
//
// Symbol resolution: `adaptive_text::measure(...)` is defined in
// `ios/AdaptiveTextMeasurer.mm`, compiled into the TextFlow pod, and
// statically linked into the host app. The XCTest bundle locates the
// symbol at link time via `BUNDLE_LOADER` / `TEST_HOST` and at runtime
// via XCTest's bundle injection. We deliberately do NOT compile the
// .mm into this test bundle — that would duplicate the symbol and the
// process-wide token-width cache, defeating both the cache invariant
// test and any future shared-state assertions.
//
// Header resolution: the
// `<react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>`
// import resolves via `$(SRCROOT)/../../common/cpp` which
// `scripts/add_test_target.rb` adds to the test target's
// HEADER_SEARCH_PATHS. The other React Native headers (`Props.h`,
// `LayoutConstraints.h`, etc.) come for free from the inherited Pods
// xcconfig (`Pods-AdaptiveTextExampleTests.debug.xcconfig`).
//
// Naming care:
//   * We deliberately use fully-qualified `facebook::react::Size` /
//     `Float` everywhere. `MacTypes.h` (pulled in transitively by
//     Foundation / UIKit) defines top-level `typedef long Size` and
//     `typedef float Float`. Using top-level `using` aliases for the
//     Fabric types collides with those — Apple's Carbon-era typedefs
//     win and the test fails to compile with "no viable conversion
//     from 'facebook::react::Size' to 'Size' (aka 'long')".
//   * `AdaptiveTextViewProps` (which inherits from Fabric's `Props`)
//     has an implicitly-deleted copy constructor — so each test
//     constructs a fresh value with direct member writes rather than
//     copying a "base" props through `auto spaced = base;`.

#import <XCTest/XCTest.h>

#import <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>

#include <cmath>
#include <limits>

namespace {

constexpr facebook::react::Float kInf =
    std::numeric_limits<facebook::react::Float>::infinity();

// Common case: no min, finite or infinite max width, height is
// unbounded (mirrors a vertically-scrollable parent).
facebook::react::LayoutConstraints constraintsWithMaxWidth(facebook::react::Float maxWidth) {
  facebook::react::LayoutConstraints c{};
  c.minimumSize = {.width = 0, .height = 0};
  c.maximumSize = {.width = maxWidth, .height = kInf};
  return c;
}

// Yoga's "EXACTLY this width" shape — `minimumSize.width ==
// maximumSize.width`. Reproduces the §4.1.5 case where the parent
// uses a fixed-width column.
facebook::react::LayoutConstraints constraintsExactWidth(facebook::react::Float width) {
  facebook::react::LayoutConstraints c{};
  c.minimumSize = {.width = width, .height = 0};
  c.maximumSize = {.width = width, .height = kInf};
  return c;
}

}  // namespace

@interface AdaptiveTextMeasurerTests : XCTestCase
@end

@implementation AdaptiveTextMeasurerTests

#pragma mark - Tokenization edge cases

- (void)testEmptyStringReturnsZeroSize {
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(kInf));
  XCTAssertEqual(s.width, 0);
  XCTAssertEqual(s.height, 0);
}

- (void)testWhitespaceOnlyStringReturnsZeroSize {
  // The word tokenizer drops empty pieces — pure whitespace yields
  // zero tokens and the measurer takes the early-return branch.
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "   ";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(kInf));
  XCTAssertEqual(s.width, 0);
  XCTAssertEqual(s.height, 0);
}

#pragma mark - Single-line layout

- (void)testSingleWordHasPositiveSize {
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Hello";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(kInf));
  // Width assertion is loose — exact glyph metrics depend on the
  // simulator's system font version. We just need it to be plausibly
  // a rendered word, not zero.
  XCTAssertGreaterThan(s.width, 10);
  // 17pt single line ≈ 20pt natural leading. Allow [16, 30] to absorb
  // any leading-related variation between iOS minor versions.
  XCTAssertGreaterThan(s.height, 16);
  XCTAssertLessThan(s.height, 30);
}

- (void)testRepeatedCallsAreIdempotent {
  // Hot-path invariant: the per-token width cache must produce
  // identical sizes across calls. A drift here would ripple through
  // every Yoga measure pass during a drag-resize and silently break
  // wrap parity over time.
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Hello world this is repeatable text";
  props.fontSize = 17;
  auto constraints = constraintsWithMaxWidth(200);

  facebook::react::Size first = adaptive_text::measure(props, {}, constraints);
  facebook::react::Size second = adaptive_text::measure(props, {}, constraints);
  facebook::react::Size third = adaptive_text::measure(props, {}, constraints);
  XCTAssertEqual(first.width, second.width);
  XCTAssertEqual(first.width, third.width);
  XCTAssertEqual(first.height, second.height);
  XCTAssertEqual(first.height, third.height);
}

#pragma mark - Wrap algorithm

- (void)testNarrowMaxWidthCausesMoreLines {
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Hello world hello world hello world hello world";
  props.fontSize = 17;

  facebook::react::Size wide = adaptive_text::measure(props, {}, constraintsWithMaxWidth(1000));
  facebook::react::Size narrow = adaptive_text::measure(props, {}, constraintsWithMaxWidth(80));
  XCTAssertLessThan(wide.height, narrow.height);  // narrow wraps to more lines
}

- (void)testFirstTokenNeverWrapsEvenWhenWiderThanMaxWidth {
  // Wrap exception: the first token on a line is placed even if its
  // own width exceeds maxWidth. (We trust the renderer to clip /
  // scale at paint time — the measurer must NOT add a phantom second
  // line just because a long single word is too wide.)
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Antidisestablishmentarianism";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(30));
  // One line at 17pt is ~20pt; assert single-line by capping height
  // generously below 2× line height.
  XCTAssertLessThan(s.height, 40);
}

#pragma mark - §4.1.4 line-height floor

- (void)testLineHeightFloorPreventsUnderReportedHeight {
  // §4.1.4 — if the supplied lineHeight is smaller than the font's
  // natural leading, the measurer must return the natural leading.
  // SwiftUI's `Text` paints at the natural height regardless;
  // under-reporting here would clip the bottom of the paragraph.
  facebook::react::AdaptiveTextViewProps base{};
  base.text = "Hi";
  base.fontSize = 33;
  facebook::react::Size natural = adaptive_text::measure(base, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps withSmallLineHeight{};
  withSmallLineHeight.text = "Hi";
  withSmallLineHeight.fontSize = 33;
  withSmallLineHeight.lineHeight = 10;  // intentionally < natural ~40pt at 33pt
  facebook::react::Size floored = adaptive_text::measure(withSmallLineHeight, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThanOrEqual(floored.height, natural.height);
}

- (void)testLineHeightAboveNaturalIsRespected {
  // Conversely, lineHeight ABOVE natural leading should grow the
  // result. Without this, custom large lineHeight props (e.g. for
  // expressive-display headlines) would silently snap to the natural
  // value and the renderer would paint inside an under-sized box.
  facebook::react::AdaptiveTextViewProps small{};
  small.text = "Hi";
  small.fontSize = 17;
  facebook::react::Size natural = adaptive_text::measure(small, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps tall{};
  tall.text = "Hi";
  tall.fontSize = 17;
  tall.lineHeight = 100;
  facebook::react::Size grown = adaptive_text::measure(tall, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThan(grown.height, natural.height);
}

#pragma mark - §4.1.5 constraints clamping

- (void)testReturnedWidthClampedUpToMinimumWidth {
  // §4.1.5 — Yoga sometimes asks us with `minimumSize.width ==
  // maximumSize.width` ("EXACTLY this width"). If our content is
  // narrower than that, we must return the minimum. Otherwise
  // YogaLayoutableShadowNode logs "AdaptiveTextView returned an
  // invalid measurement" every measure pass even though the layout
  // still works (Yoga clamps internally) — noisy and a sign that the
  // contract is being violated.
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Hi";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsExactWidth(200));
  XCTAssertEqual(s.width, 200);
}

- (void)testReturnedHeightClampedUpToMinimumHeight {
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Hi";
  props.fontSize = 17;

  facebook::react::LayoutConstraints c{};
  c.minimumSize = {.width = 0, .height = 100};
  c.maximumSize = {.width = kInf, .height = kInf};

  facebook::react::Size s = adaptive_text::measure(props, {}, c);
  XCTAssertGreaterThanOrEqual(s.height, 100);
}

- (void)testReturnedWidthClampedDownToMaximumWidth {
  // If the content's intrinsic width exceeds maxWidth — which can
  // happen when a single very long token can't be wrapped — the
  // returned width must still cap at maxWidth. The renderer is
  // responsible for handling overflow visually; the measurer's job
  // is to honor Yoga's contract.
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "Antidisestablishmentarianism";
  props.fontSize = 17;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(50));
  XCTAssertLessThanOrEqual(s.width, 50);
}

#pragma mark - Per-token width inputs

- (void)testLargerFontSizeProducesLargerSize {
  facebook::react::AdaptiveTextViewProps small{};
  small.text = "Hello";
  small.fontSize = 12;
  facebook::react::Size smallSize = adaptive_text::measure(small, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps large{};
  large.text = "Hello";
  large.fontSize = 32;
  facebook::react::Size largeSize = adaptive_text::measure(large, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThan(largeSize.width, smallSize.width);
  XCTAssertGreaterThan(largeSize.height, smallSize.height);
}

- (void)testLetterSpacingIncreasesWidth {
  facebook::react::AdaptiveTextViewProps base{};
  base.text = "Hello";
  base.fontSize = 17;
  facebook::react::Size baseSize = adaptive_text::measure(base, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps spaced{};
  spaced.text = "Hello";
  spaced.fontSize = 17;
  spaced.letterSpacing = 5;
  facebook::react::Size spacedSize = adaptive_text::measure(spaced, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThan(spacedSize.width, baseSize.width);
}

#pragma mark - Inter-token spacing

- (void)testWordSpacingIncreasesMultiWordWidth {
  // wordSpacing is the gap inserted between adjacent tokens on the
  // same line. With `wordSpacing = 0` two words pack flush; with a
  // large value the same two words measure visibly wider.
  facebook::react::AdaptiveTextViewProps base{};
  base.text = "Hello world";
  base.fontSize = 17;
  base.wordSpacing = 0;
  facebook::react::Size baseSize = adaptive_text::measure(base, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps spaced{};
  spaced.text = "Hello world";
  spaced.fontSize = 17;
  spaced.wordSpacing = 50;
  facebook::react::Size spacedSize = adaptive_text::measure(spaced, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThan(spacedSize.width, baseSize.width);
}

- (void)testLineSpacingIncreasesMultiLineHeight {
  // lineSpacing is the inter-line gutter; it only matters when wrap
  // produces ≥ 2 lines. Force wrap with a narrow maxWidth.
  facebook::react::AdaptiveTextViewProps base{};
  base.text = "one two three four five six seven eight nine ten";
  base.fontSize = 17;
  base.lineSpacing = 0;
  facebook::react::Size baseSize = adaptive_text::measure(base, {}, constraintsWithMaxWidth(80));

  facebook::react::AdaptiveTextViewProps spaced{};
  spaced.text = "one two three four five six seven eight nine ten";
  spaced.fontSize = 17;
  spaced.lineSpacing = 50;
  facebook::react::Size spacedSize = adaptive_text::measure(spaced, {}, constraintsWithMaxWidth(80));

  XCTAssertGreaterThan(spacedSize.height, baseSize.height);
}

#pragma mark - Split mode

- (void)testGraphemeSplitProducesNonZeroSize {
  // Grapheme mode should produce a sensible result for CJK text (one
  // token per character) — verifying the alternate `Tokenize()`
  // branch and that grapheme-cluster iteration doesn't choke on
  // multi-byte input.
  facebook::react::AdaptiveTextViewProps props{};
  props.text = "你好世界";
  props.fontSize = 17;
  props.splitBy = facebook::react::AdaptiveTextViewSplitBy::Grapheme;

  facebook::react::Size s = adaptive_text::measure(props, {}, constraintsWithMaxWidth(kInf));
  XCTAssertGreaterThan(s.width, 0);
  XCTAssertGreaterThan(s.height, 0);
}

#pragma mark - Font style smoke tests

- (void)testItalicStyleProducesPositiveSize {
  // Smoke test for the italic font-descriptor branch in
  // `FontFromProps`. The italic glyph advances may differ slightly
  // from regular, but we only assert that a finite positive size
  // comes back — exact equality with regular would over-pin and is
  // brittle to system-font tweaks across iOS versions.
  facebook::react::AdaptiveTextViewProps regular{};
  regular.text = "Hello";
  regular.fontSize = 17;
  facebook::react::Size r = adaptive_text::measure(regular, {}, constraintsWithMaxWidth(kInf));

  facebook::react::AdaptiveTextViewProps italic{};
  italic.text = "Hello";
  italic.fontSize = 17;
  italic.fontStyle = facebook::react::AdaptiveTextViewFontStyle::Italic;
  facebook::react::Size i = adaptive_text::measure(italic, {}, constraintsWithMaxWidth(kInf));

  XCTAssertGreaterThan(i.width, 0);
  XCTAssertGreaterThan(i.height, 0);
  // Sanity: italic shouldn't be wildly different from regular at 17pt.
  // Catches a misconfigured font descriptor that returns a fallback
  // font of a totally different size.
  XCTAssertLessThan(std::fabs((double)i.width - (double)r.width), 50);
}

- (void)testBoldWeightProducesPositiveSize {
  // Smoke test for the `UIFontWeightFromString` mapping path.
  // Numeric ("700") and named ("bold") aliases should both resolve;
  // we test the numeric form since that's what JS supplies most
  // often.
  facebook::react::AdaptiveTextViewProps bold{};
  bold.text = "Hello";
  bold.fontSize = 17;
  bold.fontWeight = "700";

  facebook::react::Size s = adaptive_text::measure(bold, {}, constraintsWithMaxWidth(kInf));
  XCTAssertGreaterThan(s.width, 0);
  XCTAssertGreaterThan(s.height, 0);
}

@end

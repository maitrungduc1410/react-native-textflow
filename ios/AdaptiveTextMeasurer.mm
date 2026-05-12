#import <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>

#import <UIKit/UIKit.h>
#import <CoreText/CoreText.h>

#import <algorithm>
#import <limits>
#import <mutex>
#import <string>
#import <unordered_map>
#import <vector>

// NOTE: do *not* use `using namespace facebook::react;` here. The macOS PCH
// brings `typedef long Size` (Carbon `MacTypes.h`) into scope, which would
// collide with `facebook::react::Size` everywhere. We fully qualify
// react-side types instead.

using AdaptiveTextViewProps = facebook::react::AdaptiveTextViewProps;
using AdaptiveTextViewSplitBy = facebook::react::AdaptiveTextViewSplitBy;
using AdaptiveTextViewFontStyle = facebook::react::AdaptiveTextViewFontStyle;
using LayoutConstraints = facebook::react::LayoutConstraints;
using LayoutContext = facebook::react::LayoutContext;

namespace {

#pragma mark - Attribute helpers

/// Translates the codegen `fontWeight` string into the matching `UIFontWeight`
/// constant. Mirrors `parseFontWeight` in `AdaptiveTextHostingView.swift`.
UIFontWeight UIFontWeightFromString(const std::string &raw) {
  if (raw == "100" || raw == "ultraLight") {
    return UIFontWeightUltraLight;
  } else if (raw == "200" || raw == "thin") {
    return UIFontWeightThin;
  } else if (raw == "300" || raw == "light") {
    return UIFontWeightLight;
  } else if (raw == "500" || raw == "medium") {
    return UIFontWeightMedium;
  } else if (raw == "600" || raw == "semibold") {
    return UIFontWeightSemibold;
  } else if (raw == "700" || raw == "bold") {
    return UIFontWeightBold;
  } else if (raw == "800" || raw == "heavy") {
    return UIFontWeightHeavy;
  } else if (raw == "900" || raw == "black") {
    return UIFontWeightBlack;
  }
  return UIFontWeightRegular;
}

/// Builds the `UIFont` that matches the props. Falls back to the system font
/// at the requested weight when no `fontFamily` is supplied. We do not apply
/// Dynamic Type scaling here because Yoga measurement runs synchronously off
/// the UI thread; the SwiftUI render layer applies scaling at draw time, but
/// for measurement purposes we use the configured point size as-is. (Dynamic
/// Type users still get the responsive layout because text content changes
/// trigger a fresh measure pass through `dirtyLayout`.)
UIFont *FontFromProps(const AdaptiveTextViewProps &props) {
  CGFloat size = props.fontSize > 0 ? (CGFloat)props.fontSize : 17.0;

  UIFont *font = nil;
  if (!props.fontFamily.empty()) {
    NSString *family = [NSString stringWithUTF8String:props.fontFamily.c_str()];
    font = [UIFont fontWithName:family size:size];
  }
  if (font == nil) {
    font = [UIFont systemFontOfSize:size weight:UIFontWeightFromString(props.fontWeight)];
  }
  if (props.fontStyle == AdaptiveTextViewFontStyle::Italic) {
    UIFontDescriptor *descriptor =
        [font.fontDescriptor fontDescriptorWithSymbolicTraits:UIFontDescriptorTraitItalic];
    if (descriptor != nil) {
      font = [UIFont fontWithDescriptor:descriptor size:0];
    }
  }
  return font;
}

/// `NSAttributedString` attribute dictionary used to size each token. Includes
/// `kerning` so `letterSpacing` participates in the width calculation.
NSDictionary<NSAttributedStringKey, id> *AttributesFromProps(const AdaptiveTextViewProps &props, UIFont *font) {
  NSMutableDictionary<NSAttributedStringKey, id> *attrs = [NSMutableDictionary new];
  attrs[NSFontAttributeName] = font;
  if (props.letterSpacing != 0) {
    attrs[NSKernAttributeName] = @((CGFloat)props.letterSpacing);
  }
  return attrs;
}

#pragma mark - Tokenization

struct Token {
  NSString *text;
  bool attachToPrevious;
};

/// `[",.!?;:)\\]}\"'"]` — same set as `tokenizeWords` in
/// `AdaptiveTextTokenizer.swift`.
NSCharacterSet *PunctuationAttachmentSet() {
  static NSCharacterSet *set;
  static dispatch_once_t onceToken;
  dispatch_once(&onceToken, ^{
    set = [NSCharacterSet characterSetWithCharactersInString:@",.!?;:)]}\"'"];
  });
  return set;
}

/// Splits `text` into word tokens. Mirrors `tokenizeWords` in
/// `AdaptiveTextTokenizer.swift`.
std::vector<Token> TokenizeWords(NSString *text) {
  std::vector<Token> tokens;
  if (text.length == 0) {
    return tokens;
  }
  NSCharacterSet *whitespace = [NSCharacterSet whitespaceAndNewlineCharacterSet];
  NSArray<NSString *> *pieces = [text componentsSeparatedByCharactersInSet:whitespace];
  NSCharacterSet *punctuationOnly = PunctuationAttachmentSet();

  for (NSString *piece in pieces) {
    if (piece.length == 0) continue;
    bool punctuationAll = true;
    for (NSUInteger i = 0; i < piece.length; ++i) {
      unichar c = [piece characterAtIndex:i];
      if (![punctuationOnly characterIsMember:c]) {
        punctuationAll = false;
        break;
      }
    }
    bool attach = punctuationAll && !tokens.empty();
    tokens.push_back({piece, attach});
  }
  return tokens;
}

/// Splits `text` into one token per grapheme cluster, dropping pure-whitespace
/// graphemes. Mirrors `tokenizeGraphemes` in `AdaptiveTextTokenizer.swift`.
std::vector<Token> TokenizeGraphemes(NSString *text) {
  __block std::vector<Token> tokens;
  NSCharacterSet *whitespace = [NSCharacterSet whitespaceAndNewlineCharacterSet];
  [text enumerateSubstringsInRange:NSMakeRange(0, text.length)
                           options:NSStringEnumerationByComposedCharacterSequences
                        usingBlock:^(NSString *substring, NSRange, NSRange, BOOL *) {
    if (substring.length == 0) return;
    NSString *trimmed = [substring stringByTrimmingCharactersInSet:whitespace];
    if (trimmed.length == 0) return;
    tokens.push_back({substring, false});
  }];
  return tokens;
}

std::vector<Token> Tokenize(NSString *text, AdaptiveTextViewSplitBy mode) {
  switch (mode) {
    case AdaptiveTextViewSplitBy::Grapheme:
      return TokenizeGraphemes(text);
    case AdaptiveTextViewSplitBy::Word:
    default:
      return TokenizeWords(text);
  }
}

#pragma mark - Layout pass

/// Per-token measurement cache so we don't re-measure repeated words.
struct TokenMetrics {
  CGSize size;
};

/// Computes the natural box for a single token under the current attributes.
/// `boundingRectWithSize:options:context:` is the same call SwiftUI uses
/// internally for `Text` sizing, so the result aligns with what we render.
CGSize MeasureTokenSize(NSString *text, NSDictionary *attributes) {
  if (text.length == 0) {
    return CGSizeZero;
  }
  NSAttributedString *attr = [[NSAttributedString alloc] initWithString:text attributes:attributes];
  CGRect rect = [attr boundingRectWithSize:CGSizeMake(CGFLOAT_MAX, CGFLOAT_MAX)
                                    options:NSStringDrawingUsesLineFragmentOrigin
                                    context:nil];
  return CGSizeMake(ceil(rect.size.width), ceil(rect.size.height));
}

#pragma mark - Token width cache

/// Process-wide token-width cache shared across every Yoga measure pass.
///
/// Keys:
///
///   * outer key — a string fingerprint of every prop that affects glyph
///     advance: `family|size|weight|italic|letterSpacing`. Anything that
///     would change a token's measured width must be in this key.
///   * inner key — the token text itself (UTF-8).
///
/// Stored value is the same `CGSize` we'd get from `MeasureTokenSize`.
///
/// Why this is the right shape for our hot path:
///
///   * On a resize drag, JS pushes a new container width every touch frame.
///     Yoga calls `measureContent` → `adaptive_text::measure` for each one,
///     re-running the *same* tokenization and *same* per-token CoreText
///     calls. The text and the font don't change between drag frames —
///     only `maxWidth` does — so the per-token sizes are identical.
///     Caching by `(font, token)` collapses N CoreText calls per frame
///     into N hashmap lookups after the first measure.
///
///   * The same cache also amortizes across multiple `<AdaptiveText>`
///     instances that share words (e.g. a list of headlines all using
///     the same body font). They populate each other's hits.
///
/// Concurrency: Yoga measure callbacks can run on the React commit thread
/// while another surface measures concurrently. We use a `std::mutex`
/// around the map operations only — `MeasureTokenSize` (CoreText) runs
/// outside the lock, so contention is bounded by hashmap work.
struct TokenWidthCache {
  std::unordered_map<std::string, std::unordered_map<std::string, CGSize>> entries;
  std::mutex mutex;
};

TokenWidthCache &SharedTokenWidthCache() {
  static TokenWidthCache cache;
  return cache;
}

/// Width-affecting props serialized into a stable string used as the outer
/// cache key. `lineHeight`, `wordSpacing`, `lineSpacing`, and `textAlign`
/// are deliberately excluded — they only influence line layout, not
/// individual glyph advance, so they never change a token's measured size.
std::string FontCacheKey(const AdaptiveTextViewProps &props) {
  std::string key;
  key.reserve(64);
  key.append(props.fontFamily);
  key.push_back('|');
  key.append(std::to_string(props.fontSize));
  key.push_back('|');
  key.append(props.fontWeight);
  key.push_back('|');
  key.push_back(props.fontStyle == AdaptiveTextViewFontStyle::Italic ? '1' : '0');
  key.push_back('|');
  key.append(std::to_string(props.letterSpacing));
  return key;
}

/// Looks up a cached size for `token` under `fontKey`, or measures it via
/// CoreText and stores it. The lock is released across the measurement so
/// other threads aren't blocked on font work.
CGSize CachedTokenSize(
    const std::string &fontKey,
    NSString *token,
    NSDictionary *attributes) {
  if (token.length == 0) {
    return CGSizeZero;
  }
  std::string textKey([token UTF8String]);

  auto &cache = SharedTokenWidthCache();
  {
    std::lock_guard<std::mutex> lock(cache.mutex);
    auto fontIt = cache.entries.find(fontKey);
    if (fontIt != cache.entries.end()) {
      auto tokenIt = fontIt->second.find(textKey);
      if (tokenIt != fontIt->second.end()) {
        return tokenIt->second;
      }
    }
  }

  CGSize size = MeasureTokenSize(token, attributes);

  {
    std::lock_guard<std::mutex> lock(cache.mutex);
    cache.entries[fontKey][textKey] = size;
  }
  return size;
}

struct LineEntry {
  CGSize size;
  CGFloat x;
};

struct Line {
  std::vector<LineEntry> entries;
  CGFloat width = 0;
  CGFloat height = 0;
};

/// Mirror of `AdaptiveTextFlowLayout.layOutLines` (swift). The wrap rule is:
/// "if the candidate width would exceed `maxWidth`, start a new line — but
/// never wrap if the token is glued to its predecessor".
std::vector<Line> LayOutLines(
    const std::vector<Token> &tokens,
    const std::vector<TokenMetrics> &metrics,
    CGFloat maxWidth,
    CGFloat horizontalSpacing) {
  std::vector<Line> lines;
  lines.emplace_back();

  for (size_t i = 0; i < tokens.size(); ++i) {
    const auto &m = metrics[i];
    bool attach = tokens[i].attachToPrevious;
    auto &current = lines.back();
    bool isFirstOnLine = current.entries.empty();
    CGFloat leadingSpace = (isFirstOnLine || attach) ? 0 : horizontalSpacing;
    CGFloat candidateRight = current.width + leadingSpace + m.size.width;

    if (candidateRight > maxWidth && !isFirstOnLine && !attach) {
      lines.emplace_back();
    }

    auto &line = lines.back();
    bool firstNow = line.entries.empty();
    CGFloat usedLeading = (firstNow || attach) ? 0 : horizontalSpacing;
    CGFloat x = line.width + usedLeading;
    line.entries.push_back({m.size, x});
    line.width = x + m.size.width;
    line.height = std::max(line.height, m.size.height);
  }
  return lines;
}

} // anonymous namespace

namespace adaptive_text {

facebook::react::Size measure(
    const AdaptiveTextViewProps &props,
    const LayoutContext & /*context*/,
    const LayoutConstraints &constraints) {
  // `context.pointScaleFactor` doesn't matter on iOS — UIKit / CoreText
  // already work in points. `fontSizeMultiplier` could be applied here if
  // we wanted to mirror Dynamic Type at measure time; today the SwiftUI
  // hosting view handles Dynamic Type itself, so the measurer can ignore
  // it without diverging.
  @autoreleasepool {
    NSString *raw = [NSString stringWithUTF8String:props.text.c_str()];
    auto tokens = Tokenize(raw, props.splitBy);
    if (tokens.empty()) {
      return facebook::react::Size{.width = 0, .height = 0};
    }

    UIFont *font = FontFromProps(props);
    NSDictionary *attributes = AttributesFromProps(props, font);
    auto fontKey = FontCacheKey(props);

    std::vector<TokenMetrics> metrics;
    metrics.reserve(tokens.size());
    for (const auto &token : tokens) {
      metrics.push_back({CachedTokenSize(fontKey, token.text, attributes)});
    }

    CGFloat maxWidth = constraints.maximumSize.width;
    if (!std::isfinite(maxWidth)) {
      maxWidth = std::numeric_limits<CGFloat>::infinity();
    }

    CGFloat horizontalSpacing = props.wordSpacing > 0 ? (CGFloat)props.wordSpacing : 6;
    CGFloat verticalSpacing = props.lineSpacing > 0 ? (CGFloat)props.lineSpacing : 4;

    // Wrap at *exactly* the same max-width SwiftUI's `AdaptiveTextFlowLayout`
    // will use when it lays the same tokens out. On iOS both sides go
    // through CoreText with the same `NSAttributedString` attributes
    // (`MeasureTokenSize` here, `Text.sizeThatFits(.unspecified)` over in
    // the renderer), so per-token widths agree to within sub-pixel
    // rounding — well below the inter-token spacing we leave on each
    // line — and there's no drift to compensate for. Android needs a
    // safety margin because Compose's `ParagraphIntrinsics` and
    // `MultiParagraphIntrinsics` paths *can* disagree by ~1 px per
    // token; iOS does not.
    //
    // We learned this the hard way: a previous revision shaved 3 pt off
    // here as a defensive hedge. At narrow Frame widths (e.g. 120 pt
    // minus 32 pt of Frame padding = 88 pt of content area) the
    // measurer's 85 pt wrap budget would push one trailing token onto a
    // new line that the renderer's 88 pt wrap never produced. Yoga then
    // sized `AdaptiveTextHostingView` for N+1 lines but SwiftUI only
    // painted N — manifesting as one full empty line at the bottom of
    // the parent frame whenever you dragged the Resizable Container
    // toward its minimum width. Keeping the two sides on the *same*
    // wrap threshold makes the bug structurally impossible.
    CGFloat wrapMaxWidth = maxWidth;

    auto lines = LayOutLines(tokens, metrics, wrapMaxWidth, horizontalSpacing);

    CGFloat totalHeight = 0;
    CGFloat widestLine = 0;
    for (size_t i = 0; i < lines.size(); ++i) {
      const auto &line = lines[i];
      // Each rendered line must accommodate both the requested `lineHeight`
      // *and* the font's intrinsic ascent+descent (`line.height`, which is
      // the max of every token's `boundingRectWithSize` height on this
      // line). SwiftUI's `Text` clamps the actual paint height to the
      // natural line height — `Font.leading(.tight)` only tightens; it
      // can't render below the glyph box — so when JS supplies a
      // `lineHeight` *smaller* than the natural one (e.g. `fontSize: 33`
      // with `lineHeight: 28`), the renderer still paints at ~40 pt per
      // line. If we report the smaller `lineHeight` here, Yoga sizes
      // `AdaptiveTextView` shorter than the painted content and the
      // bottom line(s) visibly overflow the parent (the user-reported
      // bug in the Style Morph demo). Flooring at `line.height` mirrors
      // the Android measurer and keeps our reported size ≥ what SwiftUI
      // will actually paint.
      CGFloat h = props.lineHeight > 0
          ? std::max((CGFloat)props.lineHeight, line.height)
          : line.height;
      if (i > 0) {
        totalHeight += verticalSpacing;
      }
      totalHeight += h;
      widestLine = std::max(widestLine, line.width);
    }

    // Clamp the returned size into Yoga's `[minimumSize, maximumSize]`
    // constraint box. Both axes must be honored.
    //
    // Width: when Yoga measures us with `minimumSize.width == maximumSize.width`
    // (i.e. "width is EXACTLY this"), returning `widestLine` directly violates
    // the minimum if the longest actual text line is narrower than the
    // proposed width — e.g. a 326 pt paragraph inside a 340 pt column. Yoga
    // tolerates the violation (it clamps internally and lays out fine), but
    // its `YogaLayoutableShadowNode` logs an "AdaptiveTextView returned an
    // invalid measurement" error for every such pass. The fix is to honor the
    // contract here.
    //
    // Height: same shape. We may legitimately want to *exceed*
    // `maximumSize.height` because text wraps vertically to fit width, and
    // Yoga supports that — `maximumSize.height` is +∞ in the typical scroll
    // case. We still respect `minimumSize.height` so a parent that fixed our
    // height gets the size it asked for instead of a shorter measurement.
    CGFloat width = widestLine;
    CGFloat minWidth = constraints.minimumSize.width;
    if (std::isfinite(minWidth)) {
      width = std::max(width, minWidth);
    }
    if (std::isfinite(maxWidth)) {
      width = std::min(width, maxWidth);
    }

    CGFloat height = ceil(totalHeight);
    CGFloat minHeight = constraints.minimumSize.height;
    CGFloat maxHeight = constraints.maximumSize.height;
    if (std::isfinite(minHeight)) {
      height = std::max(height, minHeight);
    }
    if (std::isfinite(maxHeight)) {
      height = std::min(height, maxHeight);
    }

    return facebook::react::Size{
        .width = static_cast<facebook::react::Float>(width),
        .height = static_cast<facebook::react::Float>(height),
    };
  }
}

} // namespace adaptive_text

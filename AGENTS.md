# AGENTS.md — orientation guide for AI coding agents (and humans)

This document is the **architectural memory** of `react-native-textflow`.
It records the structure, invariants, and the *why* behind every non-obvious
choice so that the next contributor (human or AI) can change things without
re-deriving the trade-offs from scratch.

If you're working on this repo, read this file first. The inline code
comments assume the context laid out here.

---

## 1. What this library does, in one paragraph

`<AdaptiveText>` is a Fabric component that renders a string as a flow of
**per-token native views**, where each token (word or grapheme) animates to
its new position whenever the parent's available width, the text content,
or any text style prop changes. On iOS the tokens are SwiftUI `Text` views
inside a custom SwiftUI `Layout`. On Android they are Material 3 `Text`
composables inside a custom Compose `Layout`. The look matches SwiftUI's
`Text` reflow behaviour and Material 3's `Text` motion specs.

---

## 2. Repository layout

```
src/                                      # JS façade (thin)
  AdaptiveText.tsx                        # Public component (forwards props, derives accessibilityLabel)
  AdaptiveTextNativeComponent.ts          # codegenNativeComponent spec
  types.ts                                # AdaptiveTextProps, AdaptiveAnimationConfig, …
  index.tsx                               # Public exports

common/cpp/react/renderer/components/AdaptiveTextViewSpec/
  AdaptiveTextComponentDescriptor.h       # Standard Fabric CD
  AdaptiveTextShadowNode.h                # ConcreteViewShadowNode + YogaLayoutableShadowNode
                                          #   • Owns a measure() callback that calls into the platform measurer
                                          #   • Stops Yoga from giving children a chance to lay out themselves
  AdaptiveTextMeasurer.h                  # extern "C" facade — platform must implement adaptive_text::measure(...)

ios/
  AdaptiveTextMeasurer.mm                 # CoreText-based measurer (Obj-C++); clamps to Yoga constraints
  AdaptiveTextView.{h,mm}                 # Fabric host UIView; embeds a UIHostingController
  AdaptiveTextHostingView.swift           # UIView <-> SwiftUI bridge that respects intrinsic content size
  AdaptiveTextContent.swift               # Root SwiftUI view; pushes prop changes through @ObservedObject
  AdaptiveTextFlowLayout.swift            # SwiftUI custom Layout protocol implementation
  AdaptiveTextTokenizer.swift             # Word/grapheme tokenizer + attach mask
  AdaptiveTextProps.swift                 # Swift mirror of AdaptiveTextRenderProps

android/src/main/java/com/adaptivetext/
  AdaptiveTextPackage.kt                  # ReactPackage / ViewManager registration
  AdaptiveTextViewManager.kt              # Fabric ViewManager; forwards every prop to AdaptiveTextView
  AdaptiveTextView.kt                     # FrameLayout host that owns a ComposeView (MATCH_PARENT — see §4.2)
  compose/
    AdaptiveTextRenderProps.kt            # Immutable snapshot of all props feeding one composition pass
    AdaptiveTextTokenizer.kt              # BreakIterator-based tokenizer; produces tokens + attach mask
    AdaptiveTextFlow.kt                   # Composable that renders per-token Text inside AdaptiveFlowLayout
    AdaptiveFlowLayout.kt                 # Custom Compose Layout; mirrors the Yoga measurer's wrap algorithm
  measurer/
    AdaptiveTextNativeMeasurer.kt         # Compose TextMeasurer-based measurer; cached process-wide; called from JNI

android/src/main/jni/
  AdaptiveTextMeasurer.cpp                # JNI bridge; clamps to Yoga constraints
  AdaptiveTextViewSpec.h                  # autogen wrappers
  CMakeLists.txt                          # add_library(react_codegen_AdaptiveTextViewSpec ...)

example/
  src/screens/                            # 11 demo screens (see README)
  src/components/                         # Stage / Frame / Pill / Slider — UI primitives for demos
```

---

## 3. Data & control flow on one prop change

```
JS setState
  └─▶ <AdaptiveText> re-renders
       └─▶ Fabric props diff
            ├─▶ ShadowNode.measure() (Yoga)
            │    └─▶ adaptive_text::measure(props, ctx, constraints)
            │         ├─ iOS:    AdaptiveTextMeasurer.mm     → CoreText
            │         └─ Android: AdaptiveTextMeasurer.cpp   → JNI → AdaptiveTextNativeMeasurer.kt → TextMeasurer
            │    Clamped to constraints. Returns Size{width, height}.
            └─▶ Fabric mount on the JS thread
                 └─▶ AdaptiveTextView.updateProps(props)
                      └─▶ AdaptiveTextRenderProps snapshot pushed to renderer
                           ├─ iOS: AdaptiveTextContent (SwiftUI) recomposes
                           │       └─ AdaptiveTextFlowLayout places tokens at new x/y
                           │          (.animation(curve, value: …) interpolates each token frame)
                           └─ Android: AdaptiveTextFlow (Compose) recomposes
                                   └─ AdaptiveFlowLayout places tokens
                                      (Modifier.animateTokenPlacement interpolates each token frame)
```

The render and the Yoga measurement happen on different threads and the
result of one **must agree** with the result of the other or you get visible
clipping. See §4.1.

---

## 4. Invariants you must not break

### 4.1. Measurer ↔ renderer wrap parity

The Yoga measurer's prediction of `(width, height, line count)` and the
renderer's actual layout **must** be byte-identical for the same `(text,
style, container width)` triple. Any divergence shows up as either:

- **Clipping** (renderer wraps to N lines, measurer thought N-1, height is short → bottom line cut off).
- **Empty trailing space** (measurer thought N, renderer wraps to N-1 → blank line).

The agreed shape is:

1. **Same shaper.** Both sides use Compose's `TextMeasurer` on Android and
   CoreText `CTLine` on iOS. We do *not* mix `Paragraph` and
   `MultiParagraph` measurement — they can disagree by < 1 px per token
   after sub-pixel rounding, which compounds on long paragraphs.
2. **Same wrap algorithm.** A token starts a new line iff its right edge
   would exceed `maxWidth`, with two exceptions: it's the first token on
   the line, or `attachMask[i] = true` (trailing punctuation glued to the
   prior token). Both `AdaptiveFlowLayout.kt` and
   `AdaptiveTextNativeMeasurer.kt` (and `AdaptiveTextFlowLayout.swift`)
   implement this identically — *do not refactor one without porting the
   change to the others.*
3. **6 dp horizontal safety margin** on Android. Compose's
   `ParagraphIntrinsics` (used by `TextMeasurer`) and the rendered
   `Paragraph` can still disagree by sub-pixel amounts on fractional
   densities. The margin lives in both files (`AdaptiveFlowLayout.kt`
   `WRAP_SAFETY_MARGIN`, `AdaptiveTextNativeMeasurer.kt` `safetyMarginPx`)
   and **must stay in lockstep**.
4. **Line height floor.** iOS measurer takes `max(props.lineHeight,
   line.height)` so a tiny `lineHeight` prop cannot under-report the
   actual SwiftUI `Text` line height. Mirror logic on Android.
5. **Yoga constraint clamping.** Both measurer entry points (`.mm` and
   `.cpp`) clamp the returned width/height to
   `constraints.{minimumSize,maximumSize}`. Without this Yoga logs
   "AdaptiveTextView returned an invalid measurement" when our natural
   width is less than an EXACTLY-specified minWidth (e.g. inside a
   stretched flexbox column). Yoga internally re-clamps and renders
   fine, but the log is noisy.

### 4.2. `ComposeView.layoutParams.height = MATCH_PARENT`

The Compose-side height must follow Yoga's. We learned this the hard way:

- Default was `WRAP_CONTENT`. When font preset changed `compact →
  comfortable`, Fabric correctly sized the `AdaptiveTextView` to ~497 px,
  but the `ComposeView` inside it ran its own `onMeasure` with a still-
  stale Compose tree (the previous compact-state's 332 px). The
  ComposeView's Android-side measured height became 332 px.
- Compose then recomposed with the new props and laid out 497 px of
  content inside that 332 px window. `AndroidComposeView.requestLayout()`
  fired, but Fabric's parent `ReactViewGroup` swallows that request
  (Fabric does its own layout pass; Android self-requests are not
  honoured), so the outer Android size never caught up.
- Result: bottom lines clipped.

`MATCH_PARENT` makes the outer Android size irrelevant because it always
fills whatever `AdaptiveTextView` was given by Yoga. The full extent of the
Compose tree is therefore always laid out, even if recomposition arrives a
frame later than Fabric's resize.

### 4.3. AdaptiveText is a Fabric native component — animated styles need a wrapper

`<AdaptiveText style={{ color: animValue.interpolate(...) }}>` will crash
inside RN's Animated child-tracking code (`__addChild` push on a frozen
array). To animate colors smoothly you must either:

- Wrap with `Animated.createAnimatedComponent(AdaptiveText)` and feed an
  `AnimatedProps` shape, or
- Use Reanimated 3's `useAnimatedProps` with a worklet, or
- Animate a parent `Animated.View`'s background and accept that the text
  color snap-transitions (see screen 11).

Screen 11 (`example/src/screens/11-Theme.tsx`) intentionally takes option 3,
with an explanatory comment.

### 4.4. Tokenizer + attach mask must produce identical lists on both platforms

`AdaptiveTextTokenizer.kt` and `AdaptiveTextTokenizer.swift` must yield the
same number of tokens and the same `attachMask` for the same input string,
otherwise Yoga measurement and renderer disagree on token count.

---

## 5. Performance characteristics

- **Tokenization.** O(n) over the string, memoised per `(text, splitBy)`.
- **Measurement.** Per-token widths are cached process-wide keyed by
  `(font config, token text)`. A drag-resize that ticks `maxWidth` every
  frame produces O(tokens) shaper calls *once*, then O(tokens) hashmap
  lookups for every subsequent measurement.
- **Render.** Per-token re-render is gated by Compose's smart-skip on
  Android and SwiftUI view identity on iOS. Tokens whose props are
  reference-equal between renders don't recompose.
- **Animation.** Driven by Compose's animatable system / SwiftUI's
  `.animation` modifier — runs on the OS render thread, never blocks JS.
- **No Reanimated dependency.** Animations are OS-native by design.

### Why `Modifier.animateTokenPlacement` and not `Modifier.animateBounds`?

`animateBounds` also interpolates each token's *size*, which means during
a font-size transition adjacent tokens briefly overlap and you see the
trailing glyphs of one word painted underneath the leading glyphs of the
next ("Adaptiv text..." instead of "Adaptive text..."). The custom
`animateTokenPlacement` modifier interpolates *only the placement
coordinates*; the token's size is whatever the current composition says
it is, so glyphs are never drawn out of bounds.

---

## 6. Building & running

```sh
yarn install
yarn example ios       # iOS simulator
yarn example android   # Android emulator
```

For the library itself there's no build step — TypeScript is transpiled by
`react-native-builder-bob` on publish, native code is compiled by Fabric
codegen / Gradle / CocoaPods when consumed by an app.

### Codegen

`codegen.specVersion = 3` in `package.json`. The spec is in
`src/AdaptiveTextNativeComponent.ts`. Re-run `pod install` after editing
the spec.

---

## 7. Common pitfalls

| Symptom | Likely cause |
| --- | --- |
| Bottom line clipped on Android after a prop toggle | ComposeView height stuck at WRAP_CONTENT; check §4.2 |
| "AdaptiveTextView returned an invalid measurement" log | Measurer not clamping to `constraints.minimumSize.{width,height}`; see §4.1.5 |
| Words "Adaptiv text..." with a missing letter | Renderer is animating bounds, not placement; or wrap parity off-by-one |
| Empty white space at end of a paragraph | Measurer wrapped one extra line that the renderer didn't reach |
| Crash on `AdaptiveText` `style.color` with an Animated value | See §4.3 |
| Tokens stuck at the previous palette in screen 11 after 3+ toggles | `useEffect([isTransition])` regression — must depend on `[target]` |
| `LookaheadScope` lookups return null | A token without a stable `key()` was added inside the placement modifier — every token must be `key(token.id) { … }` |

---

## 8. Adding a new feature

A. **A new text style prop** (e.g. `textDecorationLine`):

1. Add to `AdaptiveTextProps` in `src/types.ts`.
2. Add to the codegen spec in `src/AdaptiveTextNativeComponent.ts`.
3. Forward in `src/AdaptiveText.tsx`.
4. Read on the native side and thread into both `AdaptiveTextRenderProps`
   (Kotlin) and `AdaptiveTextProps` (Swift).
5. Apply in `AdaptiveTextFlow.kt` (per-token `Text(style = …)`) and in
   `AdaptiveTextContent.swift`.
6. **Critically**: if this prop affects per-token width, also include it
   in the cache key inside `AdaptiveTextNativeMeasurer.kt` and the
   equivalent attribute set in `AdaptiveTextMeasurer.mm`. Otherwise stale
   cached widths produce wrap mis-prediction.

B. **A new example screen**:

1. Add `example/src/screens/NN-MyDemo.tsx`.
2. Register in `example/src/screens/index.ts` (both `demos` and
   `RootStackParamList`).
3. Use `<Stage>` + `<Frame>` for chrome consistency. `<Pill>` and
   `<Slider>` for controls.

C. **A new platform** (e.g. web):

Add a new measurer entry under `adaptive_text::measure(...)` and a new
renderer implementation. Maintain the invariants in §4 (especially the
shared wrap algorithm).

---

## 9. Testing checklist for a measurer/renderer change

Before merging, walk through every example screen and verify:

1. Initial layout matches the design spec on both platforms.
2. **Screen 1 (Resizable):** Drag the handle slowly across its full range
   — tokens should never visibly clip or jump; the bottom line should
   always be fully visible.
3. **Screen 4 (ScrollView)** and **5 (FlatList):** Toggle the font-size
   preset compact ↔ comfortable several times. The last line in each row
   must remain fully visible.
4. **Screen 7 (StyleMorph):** Drag `fontSize` from 12 → 36 and back —
   text should never visibly overflow the frame on iOS or Android.
5. **Screen 8 (RTL):** All five `textAlign` pills should animate the
   tokens smoothly to their new line position on both platforms.
6. **Screen 11 (Theme):** Light → Dark → Light → Dark → Light. Card
   chrome animates each time; AdaptiveText snaps; nothing freezes.
7. Xcode log for screen 4 in `<ScrollView>` should not show "returned an
   invalid measurement" warnings.

---

## 10. Where to look for more context

- Inline comments in each file are the source of truth for the
  *implementation* details (cache keys, threading, etc.).
- This file is the source of truth for the *design* (invariants and
  rationale).
- The `README.md` is for *users*.

When in doubt, read the long-form comment block near the top of:

- `android/src/main/java/com/adaptivetext/AdaptiveTextView.kt` (Fabric/Compose contract)
- `android/src/main/java/com/adaptivetext/compose/AdaptiveFlowLayout.kt` (wrap parity)
- `android/src/main/java/com/adaptivetext/measurer/AdaptiveTextNativeMeasurer.kt` (cache + safety margin)
- `ios/AdaptiveTextMeasurer.mm` (lineHeight floor + clamping)
- `example/src/screens/11-Theme.tsx` (Animated-vs-Fabric color story)

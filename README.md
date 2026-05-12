# react-native-textflow

> Native SwiftUI / Jetpack Compose-style **fluid text reflow** for React Native.
> Words spring between lines as their container resizes — driven by the OS's own
> motion physics, not a JavaScript animation loop.

```tsx
<AdaptiveText
  style={{ fontSize: 22, fontWeight: '600', color: '#0e1116' }}
  animation={{ type: 'spring', damping: 18, stiffness: 220 }}
>
  When there is no more space for some words, those words smoothly fly to the next line.
</AdaptiveText>
```

## Demo

| Android | iOS |
| --- | --- |
| <video src="https://github.com/user-attachments/assets/e880e96e-1c53-4d9b-8f26-fe85118b3938" controls loop muted></video> | <video src="https://github.com/user-attachments/assets/650e1eb7-f772-4ccf-a95f-2f923dd30fe7" controls loop muted></video> |

## Why this exists

In SwiftUI and Jetpack Compose, you can render a piece of text whose words
*animate to their new positions* whenever the available width changes. It's
the polish you see in modern Apple/Google apps — and it's missing from React
Native, because `<Text>` is a single, indivisible glyph run.

`react-native-textflow` ships that exact effect as a single component.
The JavaScript layer is deliberately thin — it forwards a string + style +
animation config through Fabric codegen and lets the OS own everything else.
That's why the look is indistinguishable from a native SwiftUI / Material 3
app.

## How it works

| | iOS | Android |
| --- | --- | --- |
| Host view | `UIView` wrapping a `UIHostingController` | `FrameLayout` wrapping a `ComposeView` |
| Layout engine | SwiftUI custom `Layout` protocol (`AdaptiveTextFlowLayout`) | Jetpack Compose custom `Layout` (`AdaptiveFlowLayout`) inside `LookaheadScope` |
| Per-word motion | `.animation(_, value:)` with `interpolatingSpring` | `Modifier.animateTokenPlacement` (placement-only) |
| Yoga measurer | CoreText `boundingRectWithSize` via Obj-C++ | Compose `TextMeasurer` via JNI to Kotlin |
| Tokenizer | `String.components(separatedBy:)` / grapheme cluster iteration | `BreakIterator` |
| Min OS | iOS 16 | Android 7 (API 24), Compose BOM 2026.05+ |

Both platforms share a custom Fabric C++ shadow node (`AdaptiveTextShadowNode`)
that owns a Yoga measure callback. The C++ side delegates measurement to a
per-platform implementation that wraps text using the **same wrap algorithm**
as the renderer — byte-for-byte — so Yoga's predicted height and the
renderer's actual layout always agree. Without that lockstep, Fabric's strict
EXACTLY measure spec on `AdaptiveTextView` would clip the bottom line whenever
the two sides disagreed by even one wrap decision.

## Installation

```sh
npm install react-native-textflow
# or
yarn add react-native-textflow
```

Requires:

- React Native **0.85+** (Fabric / New Architecture)
- iOS **16.0+**
- Android **API 24+**, Jetpack Compose **BOM 2026.05+** (Compose 1.10+)

Then on iOS:

```sh
cd ios && pod install
```

## Usage

```tsx
import { AdaptiveText } from 'react-native-textflow';

<AdaptiveText style={{ fontSize: 18, color: '#111' }}>
  Hello, adaptive world.
</AdaptiveText>
```

### With a custom animation

```tsx
<AdaptiveText
  animation={{ type: 'spring', damping: 12, stiffness: 280, mass: 0.8 }}
  style={{ fontSize: 24, fontWeight: '700' }}
>
  Tweak damping and stiffness — feel the curve.
</AdaptiveText>
```

### Inside any RN container

`<AdaptiveText>` plays nicely with `<View>`, `<Modal>`, `<ScrollView>`,
`<FlatList>`, and `<Animated.View>` out of the box. See the example app for
eleven ready-made demos.

## Props

| Prop | Type | Default | Description |
| --- | --- | --- | --- |
| `children` / `text` | `string` | `''` | The text to render. Children win over `text`. |
| `style` | `StyleProp<ViewStyle \| TextStyle>` | — | RN style. Text-style keys (`fontSize`, `color`, `fontFamily`, `fontWeight`, `fontStyle`, `letterSpacing`, `lineHeight`, `textAlign`) are forwarded to the native flow layout; everything else (size, padding, background, etc.) styles the wrapper. |
| `splitBy` | `'word' \| 'grapheme'` | `'word'` | Tokenization granularity. Use `'grapheme'` for CJK or per-character flow effects. |
| `animation` | `AdaptiveAnimationConfig` | spring (18, 220, 1) | Motion curve used when the layout reflows. |
| `wordSpacing` | `number` | 6 | Horizontal spacing between tokens, in points. |
| `lineSpacing` | `number` | 4 | Vertical spacing between lines, in points. |
| `textColor` | `ColorValue` | text style `color` | Override colour applied only to the text glyphs. |
| ...rest | `ViewProps` | — | Standard view props (`testID`, `accessibility*`, layout, etc.). |

### Animation config

```ts
type AdaptiveAnimationConfig =
  | { type: 'spring'; damping?: number; stiffness?: number; mass?: number }
  | { type: 'timing'; duration?: number; easing?: 'linear' | 'easeIn' | 'easeOut' | 'easeInOut' }
  | { type: 'none' };
```

### `textAlign` and RTL

`'start'` / `'end'` flip with the active layout direction (RTL → reversed).
`'left'` / `'right'` are absolute. Toggling `textAlign` smoothly animates
every token to its new line position on both platforms.

## Accessibility

- The container exposes a single `accessibilityLabel` defaulting to the
  rendered string. Per-word native views are hidden from the accessibility
  tree, so VoiceOver and TalkBack read the phrase as one logical unit.
- Both iOS and Android respect Dynamic Type / Font Scale; pass an absolute
  `fontSize` and the OS will scale it relative to `.body` text style.
- RTL strings (Arabic, Hebrew, etc.) flow correctly when `textAlign` is
  `'start'` or `'end'`. Mirror behaviour follows `I18nManager`.

## Performance notes

- Tokenization runs once per `text` / `splitBy` change and is memoised.
- Per-token widths are cached process-wide keyed by `(font config, token text)`,
  so a drag-resize that pushes a new width per touch frame collapses N
  `TextMeasurer.measure(...)` / `boundingRectWithSize:` calls to N hash-map
  lookups after the first measurement of each token.
- Per-token re-renders are skipped via Compose's smart skipping and SwiftUI's
  view-identity tracking — only tokens whose props actually change are
  recomposed.
- The library does **not** depend on Reanimated. All motion is OS-native.
- Animation is **placement-only**: each token's intrinsic size never
  interpolates, so adjacent tokens can never overdraw each other's trailing
  glyphs (the "missing trailing letter" bug class).

## Example app

The repository ships a comprehensive example app showing `<AdaptiveText>`
inside every common React Native container:

```sh
yarn install
yarn example ios     # or: yarn example android
```

Screens:

| # | Screen | What it demonstrates |
| --- | --- | --- |
| 1 | Resizable container | Drag-handle resize; words spring per touch frame. |
| 2 | Animated width | `Animated.timing` driving the wrapper width. Reflow follows. |
| 3 | Inside a `<Modal>` | Reflow inside RN's stock modal. |
| 4 | Inside a `<ScrollView>` | Long paragraphs + sticky header; verifies measurement under prop toggles. |
| 5 | Inside a `<FlatList>` | Each row is `<AdaptiveText>`. Verifies measurement under virtualization. |
| 6 | Dynamic content | Add / remove / shuffle words; survivors slide, new ones fade in. |
| 7 | Style morphing | Sliders for fontSize / letterSpacing / lineHeight + chips for weight / italic / color. |
| 8 | RTL | Arabic & Hebrew samples × full `textAlign` matrix (animates on both platforms). |
| 9 | Animation config | Live-tune spring vs timing vs none. |
| 10 | Showcase card | Avatar + adaptive bio that grows on tap (the README screenshot). |
| 11 | Dark / Light theme | Card chrome interpolates, AdaptiveText snaps to new color (color is a non-layout prop). |

## Architecture in one diagram

```
JS:  <AdaptiveText ...props />
       │
       ▼   Fabric codegen
C++: AdaptiveTextShadowNode (LeafYogaNode + MeasurableYogaNode)
       │             │
       │             └─ measureContent()  → adaptive_text::measure(props, ctx, constraints)
       │                                     │
       │                                     ├─ iOS:    AdaptiveTextMeasurer.mm  (CoreText)
       │                                     └─ Android: AdaptiveTextMeasurer.cpp
       │                                                  └─ JNI → AdaptiveTextNativeMeasurer.kt
       │                                                            └─ Compose TextMeasurer
       │
       ▼   Fabric mount
Native view: AdaptiveTextView (UIView / FrameLayout)
       │
       ▼
Renderer:
  • iOS:    UIHostingController → AdaptiveTextContent (SwiftUI)
            └─ AdaptiveTextFlowLayout (custom Layout)
  • Android: ComposeView → AdaptiveTextFlow (Compose)
            └─ AdaptiveFlowLayout (custom Layout) inside LookaheadScope
              └─ per-token Text with Modifier.animateTokenPlacement
```

Two invariants keep the system honest:

1. **Measurer ↔ renderer wrap parity.** The Yoga measurer and the renderer
   use the same shaper (Compose `TextMeasurer` / `boundingRectWithSize:`)
   and the same wrap algorithm. A 6 dp horizontal safety margin further
   compensates for subpixel disagreement between Compose's
   `ParagraphIntrinsics` and `MultiParagraphIntrinsics` paths.
2. **`ComposeView.layoutParams.height = MATCH_PARENT`.** Pins the
   ComposeView's outer Android size to whatever Yoga gave AdaptiveTextView,
   so a one-frame-stale Compose tree can never report a smaller size that
   leaves recomposed content clipped at the bottom. (See the long-form
   comment in `AdaptiveTextView.kt` for the trace.)

## Roadmap

Deliberately out of scope for v1, planned for future versions:

- Per-word `onPress` / `onLongPress`
- Magazine-style exclusion paths (text wrapping around shapes)
- Shared-element transitions across screens
- Web platform fallback (Reanimated `LinearTransition`)
- Smoothly animated text colors (via `Animated.createAnimatedComponent` or Reanimated `useAnimatedProps`)

## Contributing

- [Development workflow](CONTRIBUTING.md#development-workflow)
- [Sending a pull request](CONTRIBUTING.md#sending-a-pull-request)
- [Code of conduct](CODE_OF_CONDUCT.md)

See [AGENTS.md](AGENTS.md) for an architecture map and the design
decisions behind every non-obvious choice in this codebase.

## License

MIT

---

Made with [create-react-native-library](https://github.com/callstack/react-native-builder-bob)

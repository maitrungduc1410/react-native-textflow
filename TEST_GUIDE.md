# Test Guide

This document explains every layer of automated testing in
`react-native-textflow` and how to run each one locally. The same layers
run in CI (`.github/workflows/ci.yml`) — see the [CI matrix](#ci-matrix)
section at the bottom for the mapping.

If you've never tested a React Native Fabric library before, **read the
[Why this many layers?](#why-this-many-layers) section first** so the
trade-offs make sense.

---

## TL;DR

```sh
# Cheap (run on every commit, < 1 min total):
yarn lint
yarn typecheck
yarn test                                          # JS unit (Jest)
cd example/android && ./gradlew :react-native-textflow:testDebugUnitTest

# Heavy (require simulator/emulator, run before merging):
cd example/android && ./gradlew :react-native-textflow:connectedDebugAndroidTest    # Android Compose UI
xcodebuild test -workspace ... -scheme AdaptiveTextExampleTests ...                 # iOS XCTest
yarn maestro:android                                                                # E2E Android
yarn maestro:ios                                                                    # E2E iOS
```

---

## Test layers at a glance

| # | Layer | Tooling | Where it lives | What it catches | Speed |
| --- | --- | --- | --- | --- | --- |
| 1 | JS unit | Jest + react-test-renderer | `src/__tests__/` | JS façade logic — prop coercion, accessibilityLabel derivation, Animated-style render path | ⚡ ~2s |
| 2 | Cross-platform fixture parity | Jest + JUnit + XCTest, all reading `__fixtures__/tokenizer.json` | `src/__tests__/`, `android/src/test/`, `example/ios/AdaptiveTextExampleTests/` | Tokenizer divergence between iOS and Android (would cause Yoga ↔ renderer wrap mis-prediction) | ⚡ ~5s |
| 3 | Android JVM unit | JUnit 4 + Truth | `android/src/test/` | Pure Kotlin logic that doesn't need an emulator (cache key behavior, etc.) | ⚡ ~30s with cold Gradle |
| 4 | Android Compose UI (instrumented) | Compose UI Testing + ActivityScenario | `android/src/androidTest/` | The §4.2 ComposeView height bug ([AGENTS.md](AGENTS.md)) — requires a real Android view tree | 🐢 ~3 min including emulator boot |
| 5 | iOS XCTest | XCTest | `example/ios/AdaptiveTextExampleTests/` | Swift tokenizer parity + the iOS Yoga measurer's wrap algorithm, line-height floor (§4.1.4), and constraints clamping (§4.1.5) | 🐢 ~2 min including simulator boot |
| 6 | E2E (Android + iOS) | Maestro | `.maestro/` | User-observable regressions — drag-resize, font-preset toggles, font-size morph, theme toggle | 🐢 ~3 min per platform |

---

## Prerequisites

You don't need every prerequisite for every layer — install what you
need.

### Always required

- **Node 20+**, **Yarn 4** (auto-managed via `packageManager` field)
- After cloning: `yarn install` once

### For iOS layers (5, 6)

- **macOS** (XCTest and iOS Simulators are Apple-only)
- **Xcode 26+** with the iOS 26 simulator runtime installed
  (Xcode → Settings → Components)
- **CocoaPods** via `bundle install` then `bundle exec pod install`
  inside `example/ios/`

### For Android layers (3, 4, 6)

- **JDK 17** (`zulu` distribution recommended; matches CI)
- **Android SDK** with API 34 platform + system images for x86_64
  (`pixel_6` profile is the CI emulator)
- An emulator booted **only** for layers 4 and 6 — layer 3 is JVM-only
  and runs on any machine

### For Maestro E2E (layer 6)

```sh
curl -fsSL "https://get.maestro.mobile.dev" | bash
# Then either restart your shell or:
export PATH="$HOME/.maestro/bin:$PATH"
maestro --version
```

Maestro doesn't need a physical device — it drives whichever
simulator/emulator is currently booted (or prompts if multiple are
running).

---

## How to run each layer

### Layer 1 — JS unit tests (Jest)

```sh
yarn test                # one-shot
yarn test:watch          # re-run on save
yarn test:coverage       # writes ./coverage
```

What's covered: `src/__tests__/AdaptiveText.test.tsx` — prop coercion,
fragment children, accessibilityLabel default, Animated style smoke
test, fontWeight/textAlign normalization. **30 tests** at last count.

### Layer 2 — Tokenizer parity

Lives across three places that all read **the same** fixture:
`__fixtures__/tokenizer.json`. There's no separate command — it's
included in the JS / Android / iOS unit-test runs. The fixture has
~12 cases (empty string, plain words, attached punctuation, whitespace,
graphemes, CJK, etc.). If you change tokenizer behavior on one
platform, the other two CI jobs will go red.

To add a case: edit `__fixtures__/tokenizer.json` and rerun layers 1, 3
and 5.

### Layer 3 — Android JVM unit tests

```sh
cd example/android
./gradlew :react-native-textflow:testDebugUnitTest
# Or run all unit tests in the library module:
./gradlew :react-native-textflow:test
```

Why `cd example/android`? The library is autolinked into the example
app; that's where Gradle has access to all RN dependencies. Running
from `android/` directly would fail because `android/` doesn't have
its own settings.gradle.

What's covered:

- `AdaptiveTextTokenizerTest.kt` — the JSON fixture parity check
- `AdaptiveTextNativeMeasurerCacheKeyTest.kt` — verifies the font cache
  key produces distinct keys for distinct font configs (a subtle bug
  here would silently feed wrong widths to Yoga; see [AGENTS.md §4.1](AGENTS.md))

Reports land at `android/build/reports/tests/testDebugUnitTest/index.html`.

### Layer 4 — Android Compose UI test (instrumented)

Requires a booted emulator (or connected device) before running:

```sh
# Boot via Android Studio AVD Manager, OR via CLI:
emulator -avd <Your_AVD_Name>

# Then:
cd example/android
./gradlew :react-native-textflow:connectedDebugAndroidTest
```

What's covered:
`android/src/androidTest/java/com/adaptivetext/AdaptiveTextViewHeightTest.kt`
— the §4.2 smoking gun. Mounts `AdaptiveTextView`, cycles between
`comfortable` ↔ `compact` font presets, and asserts that
`composeView.measuredHeight` correctly tracks the parent
`AdaptiveTextView`'s measured height across each preset change. If
the ComposeView ever sticks at `WRAP_CONTENT`, this test fails.

This is the single most important regression test in the suite. Any
change to `AdaptiveTextView.kt`, `AdaptiveFlowLayout.kt`, or the
measurer should rerun this layer.

Reports land at
`android/build/reports/androidTests/connected/debug/index.html`.

### Layer 5 — iOS XCTest

You can run via Xcode (more interactive) or the CLI (matches CI).

**Via Xcode:**

1. `cd example/ios && bundle exec pod install`
2. Open `AdaptiveTextExample.xcworkspace` (the **workspace**, not the
   `.xcodeproj`)
3. In the scheme picker, select **AdaptiveTextExampleTests**
4. Cmd-U to run

**Via CLI:**

```sh
cd example/ios
bundle install
bundle exec pod install
xcodebuild test \
  -workspace AdaptiveTextExample.xcworkspace \
  -scheme AdaptiveTextExampleTests \
  -destination 'platform=iOS Simulator,name=iPhone 17,OS=latest' \
  -resultBundlePath ./test-results.xcresult \
  CODE_SIGNING_ALLOWED=NO
```

What's covered:

- `AdaptiveTextTokenizerTests.swift` — Swift tokenizer parity with
  `__fixtures__/tokenizer.json`.
- `AdaptiveTextMeasurerTests.mm` — the iOS Yoga measurer
  (`adaptive_text::measure(...)` in `ios/AdaptiveTextMeasurer.mm`)
  end-to-end, calling the C++ entry point directly and asserting the
  invariants in [AGENTS.md §4.1](AGENTS.md): the per-token width cache
  is deterministic, the wrap algorithm doesn't add phantom lines, the
  line-height floor (§4.1.4) prevents under-reporting, and the
  constraints clamp behaves correctly when Yoga asks for an EXACTLY-
  sized box (§4.1.5). 18 tests at last count, all running in &lt; 0.1 s
  once the host app is built.

**One-time setup**: the XCTest target is generated by a Ruby script
(`example/ios/scripts/add_test_target.rb`) so it doesn't pollute the
`.xcodeproj` for non-testing contributors. The script is idempotent —
running it again on an already-configured project picks up any newly-
listed `TEST_SOURCES` (e.g. when adding a new test file) and self-
heals stale paths from older runs. CI runs it on every iOS test job.

The script wires up:

- `BUNDLE_LOADER` so the test bundle resolves
  `adaptive_text::measure` from the host app at link time (instead of
  recompiling `AdaptiveTextMeasurer.mm` into the test bundle, which
  would duplicate the process-wide token-width cache).
- `HEADER_SEARCH_PATHS` extension to `$(SRCROOT)/../../common/cpp` so
  the shared C++ headers (`<react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>`)
  resolve. The codegen-generated `Props.h` and React's
  `LayoutConstraints.h` come for free from the inherited Pods xcconfig.
- C++20 (`CLANG_CXX_LANGUAGE_STANDARD = c++20`) so designated-init
  syntax for `LayoutConstraints` compiles.

Open the resulting `.xcresult` bundle in Xcode (double-click) for a
detailed report.

### Layer 6 — Maestro E2E

The example app must be installed on the simulator/emulator first.

**Android:**

```sh
# Terminal 1: keep the example app running
yarn example android

# Terminal 2: run the flows
yarn maestro:android
```

**iOS:**

```sh
# Terminal 1: keep the example app running
yarn example ios

# Terminal 2: run the flows
yarn maestro:ios
```

You can also run a single flow:

```sh
maestro test .maestro/01-resizable.yaml
```

Or open the visual hierarchy inspector (super useful for debugging):

```sh
maestro studio
```

What's covered (`.maestro/`):

- `00-launch.yaml` — smoke test, app boots and home screen renders
- `01-resizable.yaml` — drag-handle resize across multiple widths
- `04-scrollview-preset-toggle.yaml` — the §4.2 user-observable variant
  (toggles font preset multiple times, screenshots each step for diff)
- `07-stylemorph-fontsize.yaml` — drag font-size slider 12↔36
- `11-theme-toggle.yaml` — Light → Dark → … cycle (§4.3 guard)

Outputs land in `.maestro-output/` (gitignored) — a folder of
screenshots plus, in CI, `maestro-junit.xml`.

#### When iOS flows fail

Almost every Maestro-iOS surprise we've hit so far falls into one of
four buckets. Read these *before* burning a debugging round:

1. **iOS auto-combines `<Pressable>` / parent-`accessible` child
   labels.** A `Pressable` with multiple `Text` children produces a
   single accessibility label of `"child1 child2 child3"` joined with
   spaces. Maestro's `text:` matcher uses *full-string regex*, so
   `text: "Resizable container"` won't match `"01 Resizable container Drag the handle…"`.
   Fix: add `testID` and use `id:` in the flow.
2. **Plain `<View>`s aren't queryable on iOS unless `accessible={true}`.**
   `testID` alone sets `accessibilityIdentifier` but doesn't set
   `isAccessibilityElement = YES`, and Maestro's iOS driver only sees
   accessibility-element views. Fix: `<View accessible testID="…">`.
3. **Sibling `<Text>`s in a flex row collapse on iOS.** Same root cause
   as bucket 1, but for `View`-with-`Text`-children rather than
   `Pressable`. The Slider's outer wrapper hits this; the fix is the
   same as bucket 2 (add `accessible` to the wrapper, target by id).
4. **iOS `interactivePopGestureRecognizer` competes with horizontal
   swipe gestures inside the screen.** Maestro's synthesised
   long-distance horizontal swipe can pop the screen back to home
   mid-flow, leaving subsequent steps unable to find their `from:`
   element. Fix: `options: { gestureEnabled: false }` on screens with
   horizontal-drag interactions (see `example/src/screens/index.ts`'s
   `ResizableContainer` entry).

#### Debugging recipe

```sh
# 1. Manually navigate to the screen the failing step targets.
# 2. With the simulator on that screen, dump the live AT tree:
maestro --device <UDID> hierarchy > .maestro-output/debug.json
# 3. Search the JSON for the identifier or text you're matching against.
#    If it's not there, you've got an accessibility issue (buckets 2 / 3).
#    If it's there, the issue is timing or gesture interference.
```

`<UDID>` for the booted iOS simulator:

```sh
xcrun simctl list devices booted
```

---

## Lint and typecheck

```sh
yarn lint           # ESLint (also covers Prettier)
yarn lint --fix     # auto-fix what's auto-fixable
yarn typecheck      # tsc --noEmit
```

Lint rules live in `eslint.config.mjs`. The `.maestro/` and
`coverage/` directories are excluded.

---

## CI matrix

The same layers run in `.github/workflows/ci.yml`. They're split into a
**cheap lane** (every commit / PR) and a **heavy lane** (push to
`master` only). All test jobs are **platform-scoped** via
`paths-filter`: an Android-test job won't run on an iOS-only diff, and
vice versa.

| Job | Lane | OS | Trigger paths | What it runs |
| --- | --- | --- | --- | --- |
| `lint` | cheap | Ubuntu | always | `yarn lint`, `yarn typecheck` |
| `build-library` | cheap | Ubuntu | always | `yarn prepare` (bob build) |
| `test-js` | cheap | Ubuntu | always | layer 1 (Jest), uploads coverage |
| `test-android-unit` | cheap | Ubuntu | Android-relevant\* | layer 3 (Gradle JVM tests) |
| `test-ios` | cheap | macOS | iOS-relevant\* | layer 5 (XCTest), uploads xcresult |
| `build-android` | cheap | Ubuntu | always | full example app Android build |
| `build-ios` | cheap | macOS | always | full example app iOS build |
| `test-android-instrumented` | **heavy** | Ubuntu + KVM | Android-relevant\* | layer 4 (Compose UI) |
| `maestro-android` | **heavy** | Ubuntu + KVM | Android-relevant\* + `.maestro/` + `example/android/` + `example/src/` | layer 6a (E2E Android) |
| `maestro-ios` | **heavy** | macOS | iOS-relevant\* + `.maestro/` + `example/ios/` + `example/src/` | layer 6b (E2E iOS) |

\* **Android-relevant**: `android/**`, `src/**`, `common/**`, `__fixtures__/**`. **iOS-relevant**: `ios/**`, `src/**`, `common/**`, `__fixtures__/**`. `src/` and `common/` count for both because the JS façade and shared C++ Yoga shadow node feed both platforms.

In practice this means:

- A diff touching only `ios/**` skips every Android test job (cheap and
  heavy) and only runs iOS tests.
- A diff touching only `android/**` skips every iOS test job and only
  runs Android tests.
- A diff touching `src/`, `common/`, `__fixtures__/`, or `package.json`
  runs both platforms' test jobs.
- A pure README / docs diff runs only the always-on jobs (`lint`,
  `build-library`, `test-js`).

---

## Why this many layers?

A Fabric library has **three concurrency boundaries** that any change
can break:

1. **JS ↔ codegen ↔ native** — props pipeline
2. **Yoga measure thread ↔ render thread** — the wrap-parity invariant
   (AGENTS.md §4.1)
3. **Native view ↔ accessibility tree** — only visible to E2E tooling

A unit test catches none of #2 or #3. An E2E test catches all three but
takes 30× longer to run and gives you "test failed" instead of "this
specific function returned the wrong value." The layers exist so each
boundary is guarded by the cheapest tool that can guard it:

- Pure functions → Jest / JUnit / XCTest (milliseconds)
- View construction → Compose UI test (~minutes, but isolates Android
  from iOS)
- User-observable behavior → Maestro (slowest, last line of defense)

The §4.2 bug in particular *cannot* be unit-tested in JS or pure
Kotlin: it requires the actual Android `View.onMeasure` cycle to fire
twice (Fabric's pass and ComposeView's internal pass) and disagree.
The instrumented test in layer 4 is a smaller, faster way to reproduce
it than running the example app and toggling the preset by hand. The
Maestro flow in layer 6 confirms the same regression at the user-
observable level.

If you're tempted to "just delete the heavy lane and rely on units" —
read AGENTS.md §4 first. The heavy lane is the only thing that catches
~half the invariants documented there.

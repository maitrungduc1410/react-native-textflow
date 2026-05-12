import { useEffect, useMemo, useRef, useState } from 'react';
import {
  Animated,
  Easing,
  StyleSheet,
  Text,
  View,
  useColorScheme,
} from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';

/**
 * Dark / Light theme example.
 *
 * Two pieces interact here:
 *
 *   1. **Card chrome** (background, border, eyebrow / hint colors) is
 *      wrapped in `Animated.View` / `Animated.Text` and gets *interpolated*
 *      colors that glide smoothly between the active and previous palette
 *      via a single `Animated.Value 0→1` driving per-channel
 *      `interpolate({ outputRange })`. One value, six channels — much
 *      cheaper than animating each color separately.
 *
 *   2. **AdaptiveText** receives *static* (snapped) colors from the
 *      resolved palette. Trying to pass an `AnimatedInterpolation` as
 *      `style.color` to a Fabric-native component blows up inside RN's
 *      Animated child-tracking code (a frozen-array `__addChild` crash).
 *      Smoothly animating native-prop colors would require either
 *      `Animated.createAnimatedComponent(AdaptiveText)` + a custom prop
 *      marshaller or Reanimated 3's `useAnimatedProps`. For an example
 *      screen the snap-transition is the right tradeoff; the snap is
 *      barely visible because the card behind the text fades in the
 *      same 320 ms window.
 *
 * Also serves as a subtle demonstration that `color` is a *non-layout*
 * prop on AdaptiveText — tokens stay in place when only color changes;
 * the reflow engine doesn't run.
 */

const HEADLINE = 'Adaptive text adopts the active theme — instantly.';
const BODY =
  'Switch modes and watch the card glide between palettes. Text color snaps on each token; the surrounding chrome interpolates.';
const ACCENT = 'Material 3 · SwiftUI · One reflow engine.';

type Mode = 'light' | 'dark' | 'system';

type Palette = {
  bg: string;
  card: string;
  border: string;
  headline: string;
  body: string;
  accent: string;
  caption: string;
};

const LIGHT: Palette = {
  bg: '#f6f7f9',
  card: '#ffffff',
  border: '#e6e9ee',
  headline: '#0e1116',
  body: '#1f2937',
  accent: '#2563eb',
  caption: '#5e6772',
};

const DARK: Palette = {
  bg: '#0b0e13',
  card: '#161a22',
  border: '#262d3a',
  headline: '#f8fafc',
  body: '#cbd5e1',
  accent: '#60a5fa',
  caption: '#94a3b8',
};

/**
 * Drives a 0→1 Animated.Value across a theme change and exposes per-channel
 * `AnimatedInterpolation` strings for *non-text chrome* (card bg, border,
 * eyebrow / hint label colors).
 *
 * We do not feed these to `<AdaptiveText>`: AdaptiveText is a Fabric native
 * component and its props pipeline doesn't know how to consume RN's
 * `AnimatedInterpolation` objects. Passing one as `style.color` blows up
 * inside RN's Animated child-tracking code with a frozen-array
 * `__addChild` error. Keep animated colors on `Animated.View` /
 * `Animated.Text`; let AdaptiveText receive the resolved (snapped) target
 * colors. The card chrome interpolates smoothly between palettes; the
 * text color snap-transitions — a reasonable, crash-free middle ground
 * for an example screen.
 *
 * Why the from/to refs are rotated *during render* (not in useEffect):
 *
 *   `useMemo` builds the `Animated.interpolate(...)` nodes synchronously
 *   during render, but `useEffect` only fires **after** the render commit.
 *   If we rotate the refs inside the effect, the very render that observes
 *   a new `target` builds its interpolation from the *previous* render's
 *   refs — which on a toggle gives `outputRange = [previousPrevious,
 *   previous]`, not `[previous, current]`. We hit that bug live:
 *
 *     * 1st toggle (LIGHT → DARK): refs are still [LIGHT, LIGHT] when
 *       useMemo runs, so the interpolation is [LIGHT, LIGHT]. The effect
 *       animates progress 0→1, but every channel interpolates LIGHT →
 *       LIGHT and the card stays light while the DARK pill is highlighted.
 *
 *     * 2nd toggle (back to LIGHT): refs have caught up to [LIGHT, DARK]
 *       from the previous effect, so useMemo builds [LIGHT, DARK]. The
 *       effect re-runs animating 0→1, so the card animates *toward* DARK
 *       while the LIGHT pill is highlighted.
 *
 *   Rotating refs synchronously during render — and snapping `progress`
 *   to 0 in the same place — makes the interpolation built this render
 *   correct from the very first painted frame, and the
 *   `Animated.timing` in the effect just drives `progress` 0→1.
 *   Mutating refs during render is allowed for the "track previous prop"
 *   pattern; React's rendering-consistency rules apply to state and
 *   reads, not to ref writes.
 */
function useInterpolatedPalette(target: Palette): Palette {
  const progress = useRef(new Animated.Value(0)).current;
  const fromRef = useRef<Palette>(target);
  const toRef = useRef<Palette>(target);

  if (toRef.current !== target) {
    fromRef.current = toRef.current;
    toRef.current = target;
    progress.setValue(0);
  }

  useEffect(() => {
    // Skip the initial mount: when `from === to` there's no real transition,
    // just the default `progress = 0` display of the starting palette.
    if (fromRef.current === toRef.current) return;
    // Depending on `target` (not a boolean flag) is what makes this fire on
    // *every* toggle. We tried `[isTransition]` previously and it broke
    // after the third toggle: `isTransition` stays `true` between any two
    // consecutive non-equal targets (DARK→LIGHT, then LIGHT→DARK both
    // produce `true`), so React's `Object.is` dep check decided the deps
    // hadn't changed and skipped the effect — leaving `progress` stuck at
    // 0 and the card chrome frozen at `fromRef`'s palette while the new
    // pill said otherwise. `target` is a stable `LIGHT`/`DARK` reference
    // that flips identity on every toggle, so the effect re-runs cleanly.
    Animated.timing(progress, {
      toValue: 1,
      duration: 320,
      easing: Easing.bezier(0.2, 0.8, 0.2, 1),
      useNativeDriver: false,
    }).start();
    // `progress` is a stable ref-held Animated.Value; depending on it
    // would re-run this effect after StrictMode's dev remount.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [target]);

  return useMemo(() => {
    const interp = (key: keyof Palette) =>
      progress.interpolate({
        inputRange: [0, 1],
        outputRange: [fromRef.current[key], toRef.current[key]],
      }) as unknown as string;
    return {
      bg: interp('bg'),
      card: interp('card'),
      border: interp('border'),
      headline: interp('headline'),
      body: interp('body'),
      accent: interp('accent'),
      caption: interp('caption'),
    };
  }, [progress]);
}

export default function ThemeScreen() {
  const [mode, setMode] = useState<Mode>('light');
  const systemScheme = useColorScheme();

  const resolved: Palette = useMemo(() => {
    if (mode === 'system') return systemScheme === 'dark' ? DARK : LIGHT;
    return mode === 'dark' ? DARK : LIGHT;
  }, [mode, systemScheme]);

  const palette = useInterpolatedPalette(resolved);

  return (
    <Stage caption="Switch theme. Card chrome interpolates; text snaps to the new color (AdaptiveText is native — no animated colors).">
      <View style={styles.row}>
        {(['light', 'dark', 'system'] as Mode[]).map((m) => (
          <Pill
            key={m}
            label={m}
            onPress={() => setMode(m)}
            selected={mode === m}
          />
        ))}
      </View>

      <Animated.View style={[styles.canvas, { backgroundColor: palette.bg }]}>
        <Animated.View
          style={[
            styles.card,
            {
              backgroundColor: palette.card,
              borderColor: palette.border,
            },
          ]}
        >
          <Animated.Text style={[styles.eyebrow, { color: palette.caption }]}>
            THEME · {mode === 'system' ? `system (${systemScheme})` : mode}
          </Animated.Text>

          <AdaptiveText style={[styles.headline, { color: resolved.headline }]}>
            {HEADLINE}
          </AdaptiveText>

          <AdaptiveText style={[styles.body, { color: resolved.body }]}>
            {BODY}
          </AdaptiveText>

          <AdaptiveText style={[styles.accent, { color: resolved.accent }]}>
            {ACCENT}
          </AdaptiveText>
        </Animated.View>

        <Text style={[styles.hint, { color: resolved.caption }]}>
          color is a non-layout prop — tokens stay put while the card glides.
        </Text>
      </Animated.View>
    </Stage>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 8,
  },
  canvas: {
    flex: 1,
    borderRadius: 16,
    padding: 16,
    gap: 16,
  },
  card: {
    borderRadius: 14,
    borderWidth: 1,
    padding: 18,
    gap: 12,
  },
  eyebrow: {
    fontSize: 11,
    fontWeight: '700',
    letterSpacing: 1.2,
  },
  headline: {
    fontSize: 22,
    fontWeight: '700',
    lineHeight: 30,
  },
  body: {
    fontSize: 15,
    lineHeight: 22,
    fontWeight: '500',
  },
  accent: {
    fontSize: 13,
    fontWeight: '600',
    letterSpacing: 0.4,
  },
  hint: {
    fontSize: 12,
    textAlign: 'center',
  },
});

import { useEffect, useRef, useState } from 'react';
import {
  PanResponder,
  StyleSheet,
  Text,
  View,
  type LayoutChangeEvent,
} from 'react-native';

type Props = {
  label: string;
  value: number;
  min: number;
  max: number;
  step?: number;
  unit?: string;
  onChange: (value: number) => void;
};

/**
 * Tiny dependency-free slider. Tracks layout once and converts horizontal
 * drag distance into the value range.
 *
 * Implementation note: `PanResponder.create` is created exactly once (so
 * gestures stay attached across re-renders) and reads through refs that
 * are kept in sync via `useEffect`. Capturing `value`/`width`/`min`/`max`
 * directly in the responder closures would freeze them at mount time,
 * which broke the slider on the first frame because `width` was still 0.
 */
export function Slider({
  label,
  value,
  min,
  max,
  step = 1,
  unit,
  onChange,
}: Props) {
  const [width, setWidth] = useState(0);

  const valueRef = useRef(value);
  const widthRef = useRef(0);
  const minRef = useRef(min);
  const maxRef = useRef(max);
  const stepRef = useRef(step);
  const onChangeRef = useRef(onChange);
  useEffect(() => {
    valueRef.current = value;
    minRef.current = min;
    maxRef.current = max;
    stepRef.current = step;
    onChangeRef.current = onChange;
  });

  const startValue = useRef(value);

  const responder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponderCapture: () => true,
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: (e) => {
        const w = widthRef.current;
        startValue.current = valueRef.current;
        if (w <= 0) return;
        // Treat a tap on the track as a jump-to-position.
        const x = e.nativeEvent.locationX;
        applyAt(x);
      },
      onPanResponderMove: (_e, g) => {
        const w = widthRef.current;
        if (w <= 0) return;
        const delta = (g.dx / w) * (maxRef.current - minRef.current);
        applyValue(startValue.current + delta);
      },
    })
  ).current;

  function applyAt(x: number) {
    const w = widthRef.current;
    if (w <= 0) return;
    const ratio = Math.max(0, Math.min(1, x / w));
    applyValue(minRef.current + ratio * (maxRef.current - minRef.current));
  }

  function applyValue(raw: number) {
    const s = stepRef.current;
    const stepped = Math.round(raw / s) * s;
    const clamped = Math.max(minRef.current, Math.min(maxRef.current, stepped));
    if (clamped !== valueRef.current) {
      valueRef.current = clamped;
      onChangeRef.current(clamped);
    }
  }

  const ratio = width > 0 ? (value - min) / (max - min) : 0;

  return (
    <View style={styles.row}>
      <View style={styles.header}>
        <Text style={styles.label}>{label}</Text>
        <Text style={styles.value}>
          {value}
          {unit ?? ''}
        </Text>
      </View>
      <View
        style={styles.track}
        onLayout={(e: LayoutChangeEvent) => {
          const w = e.nativeEvent.layout.width;
          widthRef.current = w;
          setWidth(w);
        }}
        {...responder.panHandlers}
      >
        <View style={styles.rail} />
        <View style={[styles.fill, { width: `${ratio * 100}%` }]} />
        <View style={[styles.thumb, { left: `${ratio * 100}%` }]} />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    gap: 6,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  label: {
    fontSize: 13,
    color: '#5e6772',
    fontWeight: '600',
  },
  value: {
    fontSize: 13,
    color: '#0f172a',
    fontWeight: '700',
    fontVariant: ['tabular-nums'],
  },
  track: {
    height: 32,
    justifyContent: 'center',
  },
  rail: {
    position: 'absolute',
    left: 0,
    right: 0,
    height: 4,
    backgroundColor: '#e2e8f0',
    borderRadius: 2,
  },
  fill: {
    position: 'absolute',
    height: 4,
    backgroundColor: '#3b82f6',
    borderRadius: 2,
  },
  thumb: {
    position: 'absolute',
    width: 22,
    height: 22,
    marginLeft: -11,
    borderRadius: 11,
    backgroundColor: '#3b82f6',
    borderWidth: 3,
    borderColor: '#ffffff',
  },
});

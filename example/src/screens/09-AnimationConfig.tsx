import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import {
  AdaptiveText,
  type AdaptiveAnimationConfig,
} from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';
import { Slider } from '../components/Slider';
import { Frame } from '../components/Frame';

const SAMPLES = [
  'Adaptive text animates with a spring.',
  'Adaptive text animates with a spring, and you can tune every parameter live.',
  'Adaptive text animates with a spring; tune damping, stiffness, and mass to see how the OS-level interpolation responds.',
];

type Kind = 'spring' | 'timing' | 'none';

export default function AnimationConfig() {
  const [kind, setKind] = useState<Kind>('spring');
  const [damping, setDamping] = useState(18);
  const [stiffness, setStiffness] = useState(220);
  const [duration, setDuration] = useState(250);
  const [easing, setEasing] = useState<
    'linear' | 'easeIn' | 'easeOut' | 'easeInOut'
  >('easeInOut');
  const [sampleIdx, setSampleIdx] = useState(0);

  const animation: AdaptiveAnimationConfig =
    kind === 'spring'
      ? { type: 'spring', damping, stiffness, mass: 1 }
      : kind === 'timing'
        ? { type: 'timing', duration, easing }
        : { type: 'none' };

  return (
    <Stage caption="Tap a sample and watch reflow with the active curve. Tune below.">
      <View style={styles.row}>
        {(['spring', 'timing', 'none'] as Kind[]).map((k) => (
          <Pill
            key={k}
            label={k}
            onPress={() => setKind(k)}
            selected={kind === k}
          />
        ))}
      </View>

      <Frame>
        <AdaptiveText style={styles.text} animation={animation}>
          {SAMPLES[sampleIdx % SAMPLES.length]!}
        </AdaptiveText>
      </Frame>

      <View style={styles.row}>
        <Pill label="Cycle text" onPress={() => setSampleIdx((i) => i + 1)} />
      </View>

      {kind === 'spring' ? (
        <View style={styles.controls}>
          <Slider
            label="Damping"
            value={damping}
            min={4}
            max={40}
            onChange={setDamping}
          />
          <Slider
            label="Stiffness"
            value={stiffness}
            min={50}
            max={600}
            step={10}
            onChange={setStiffness}
          />
        </View>
      ) : kind === 'timing' ? (
        <View style={styles.controls}>
          <Slider
            label="Duration"
            unit="ms"
            value={duration}
            min={50}
            max={1500}
            step={50}
            onChange={setDuration}
          />
          <View style={styles.row}>
            {(['linear', 'easeIn', 'easeOut', 'easeInOut'] as const).map(
              (e) => (
                <Pill
                  key={e}
                  label={e}
                  onPress={() => setEasing(e)}
                  selected={easing === e}
                />
              )
            )}
          </View>
        </View>
      ) : null}
    </Stage>
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  text: {
    fontSize: 20,
    fontWeight: '600',
    color: '#0e1116',
    lineHeight: 30,
  },
  controls: {
    gap: 14,
    marginTop: 8,
  },
});

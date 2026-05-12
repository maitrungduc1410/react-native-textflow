import { useEffect, useRef, useState } from 'react';
import { Animated, Easing, StyleSheet, View } from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';

const SAMPLE =
  'Adaptive text reflows in lockstep with whatever animates the parent — no extra wiring required.';

type Size = 'narrow' | 'medium' | 'wide';
const SIZES: Record<Size, number> = { narrow: 160, medium: 260, wide: 360 };

export default function AnimatedWidth() {
  const [size, setSize] = useState<Size>('medium');
  const animated = useRef(new Animated.Value(SIZES.medium)).current;

  useEffect(() => {
    Animated.timing(animated, {
      toValue: SIZES[size],
      duration: 600,
      easing: Easing.bezier(0.2, 0.8, 0.2, 1),
      useNativeDriver: false,
    }).start();
  }, [size, animated]);

  return (
    <Stage caption="The wrapping <View> animates with Animated.timing. AdaptiveText follows naturally.">
      <View style={styles.row}>
        {(Object.keys(SIZES) as Size[]).map((key) => (
          <Pill
            key={key}
            label={key}
            onPress={() => setSize(key)}
            selected={size === key}
          />
        ))}
      </View>
      <View style={styles.canvas}>
        <Animated.View style={[styles.frame, { width: animated }]}>
          <AdaptiveText style={styles.text}>{SAMPLE}</AdaptiveText>
        </Animated.View>
      </View>
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
    alignItems: 'center',
    justifyContent: 'center',
  },
  frame: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e6e9ee',
  },
  text: {
    fontSize: 18,
    lineHeight: 26,
    color: '#0e1116',
    fontWeight: '500',
  },
});

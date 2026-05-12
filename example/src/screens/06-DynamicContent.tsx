import { useState } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Frame } from '../components/Frame';

const POOL = [
  'fluid',
  'native',
  'composable',
  'observable',
  'animated',
  'responsive',
  'kinetic',
  'lyrical',
  'precise',
  'springy',
  'elegant',
  'declarative',
];

const SEED = ['Adaptive', 'text', 'is'];

export default function DynamicContent() {
  const [words, setWords] = useState<string[]>(SEED);

  const text = `${words.join(' ')}.`;

  function add() {
    const remaining = POOL.filter((w) => !words.includes(w));
    if (!remaining.length) return;
    const pick = remaining[Math.floor(Math.random() * remaining.length)]!;
    setWords([...words, pick]);
  }

  function remove() {
    if (words.length <= SEED.length) return;
    setWords(words.slice(0, -1));
  }

  function shuffle() {
    setWords([...words].sort(() => Math.random() - 0.5));
  }

  function reset() {
    setWords(SEED);
  }

  return (
    <Stage caption="Add, remove, shuffle, or reset. Survivors slide; new tokens fade in; removed tokens fade out.">
      <View style={styles.actions}>
        <Action label="Add" onPress={add} variant="primary" />
        <Action label="Remove" onPress={remove} />
        <Action label="Shuffle" onPress={shuffle} />
        <Action label="Reset" onPress={reset} />
      </View>
      <Frame>
        <AdaptiveText style={styles.text}>{text}</AdaptiveText>
      </Frame>
      <Text style={styles.counter}>{words.length} words</Text>
    </Stage>
  );
}

function Action({
  label,
  onPress,
  variant,
}: {
  label: string;
  onPress: () => void;
  variant?: 'primary';
}) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.action,
        variant === 'primary' && styles.actionPrimary,
        pressed && styles.pressed,
      ]}
    >
      <Text
        style={[
          styles.actionLabel,
          variant === 'primary' && styles.actionLabelPrimary,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  actions: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  action: {
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: '#cbd5e1',
    backgroundColor: '#ffffff',
  },
  actionPrimary: {
    backgroundColor: '#0f172a',
    borderColor: '#0f172a',
  },
  pressed: {
    opacity: 0.7,
  },
  actionLabel: {
    fontSize: 13,
    fontWeight: '600',
    color: '#0f172a',
  },
  actionLabelPrimary: {
    color: '#ffffff',
  },
  text: {
    fontSize: 22,
    fontWeight: '600',
    color: '#0e1116',
    lineHeight: 30,
  },
  counter: {
    fontSize: 12,
    color: '#5e6772',
    fontVariant: ['tabular-nums'],
    marginTop: 8,
  },
});

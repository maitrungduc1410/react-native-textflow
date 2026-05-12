import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';

const PARAGRAPHS = [
  'Adaptive text was designed first and foremost for content that lives inside a scrollable surface. The flow layout reports its own intrinsic height to React Native’s Yoga engine, so vertical scrolling just works.',
  'When the column width changes — because the user toggled a sidebar, switched orientation, or dragged a Stage Manager window — every paragraph reflows in unison, with each word springing to its new line.',
  'Because every word is a real native view, accessibility tools see one logical phrase via accessibilityLabel, while screen-readers can navigate paragraphs as a whole.',
];

export default function ScrollViewScreen() {
  const [size, setSize] = useState<'compact' | 'comfortable'>('comfortable');
  const fontSize = size === 'compact' ? 15 : 18;
  const lineHeight = size === 'compact' ? 22 : 28;

  return (
    <Stage caption="Scroll vertically; tap a width preset to see the column reflow.">
      <View style={styles.toolbar}>
        <Pill
          label="compact"
          onPress={() => setSize('compact')}
          selected={size === 'compact'}
        />
        <Pill
          label="comfortable"
          onPress={() => setSize('comfortable')}
          selected={size === 'comfortable'}
        />
      </View>
      <ScrollView
        contentContainerStyle={styles.content}
        stickyHeaderIndices={[0]}
      >
        <View style={styles.stickyHeader}>
          <AdaptiveText style={styles.stickyText}>
            Sticky header — adaptive even when pinned
          </AdaptiveText>
        </View>
        {PARAGRAPHS.map((p, i) => (
          <View key={i} style={styles.card}>
            <Text style={styles.idx}>{`§ ${i + 1}`}</Text>
            <AdaptiveText style={[styles.paragraph, { fontSize, lineHeight }]}>
              {p}
            </AdaptiveText>
          </View>
        ))}
      </ScrollView>
    </Stage>
  );
}

const styles = StyleSheet.create({
  toolbar: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 8,
  },
  content: {
    paddingBottom: 32,
    gap: 12,
  },
  stickyHeader: {
    backgroundColor: '#0f172a',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 10,
  },
  stickyText: {
    fontSize: 14,
    fontWeight: '600',
    color: '#ffffff',
    letterSpacing: 0.4,
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e6e9ee',
    gap: 8,
  },
  idx: {
    fontSize: 12,
    color: '#94a3b8',
    fontWeight: '700',
    letterSpacing: 1,
  },
  paragraph: {
    color: '#1f2937',
    fontWeight: '500',
  },
});

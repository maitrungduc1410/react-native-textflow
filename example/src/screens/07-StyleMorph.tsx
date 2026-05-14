import { useState } from 'react';
import { ScrollView, StyleSheet, Text, View } from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Slider } from '../components/Slider';
import { Frame } from '../components/Frame';
import { Pill } from '../components/Pill';

/**
 * Style morphing example.
 *
 * Demonstrates that every typography prop participates in the same reflow
 * pipeline as `text` itself — changing `fontSize`, `letterSpacing`,
 * `lineHeight`, `fontWeight`, `fontStyle`, or `color` re-runs Yoga
 * measurement (because per-token widths change with the font config) and
 * triggers a smooth per-token re-flow on both iOS and Android. The screen
 * is wrapped in a `ScrollView` because the chip rows (Weight / Style /
 * Color) push the controls off small-phone screens otherwise.
 */

const SAMPLE =
  'Glyphs change shape as you tweak the metrics; words realign in real time, exactly as a Material 3 Text or SwiftUI Text would.';

const WEIGHTS = ['300', '400', '500', '600', '700', '800'] as const;
type Weight = (typeof WEIGHTS)[number];

const STYLES = ['normal', 'italic'] as const;
type FontStyleOpt = (typeof STYLES)[number];

const COLOR_OPTIONS: { id: string; label: string; value: string }[] = [
  { id: 'ink', label: 'ink', value: '#0e1116' },
  { id: 'blue', label: 'blue', value: '#2563eb' },
  { id: 'magenta', label: 'magenta', value: '#db2777' },
  { id: 'emerald', label: 'emerald', value: '#059669' },
  { id: 'amber', label: 'amber', value: '#d97706' },
];

export default function StyleMorph() {
  const [fontSize, setFontSize] = useState(20);
  const [letterSpacing, setLetterSpacing] = useState(0);
  const [lineHeight, setLineHeight] = useState(28);
  const [weight, setWeight] = useState<Weight>('600');
  const [fontStyle, setFontStyle] = useState<FontStyleOpt>('normal');
  const [colorId, setColorId] = useState<string>('ink');

  const color = COLOR_OPTIONS.find((c) => c.id === colorId)!.value;

  return (
    <Stage caption="Drag a slider or tap a chip. Glyph metrics change → flow recomputes → words slide.">
      <ScrollView
        contentContainerStyle={styles.scrollContent}
        showsVerticalScrollIndicator={false}
      >
        <Frame>
          <AdaptiveText
            style={[
              styles.text,
              {
                fontSize,
                letterSpacing,
                lineHeight,
                fontWeight: weight,
                fontStyle,
                color,
              },
            ]}
          >
            {SAMPLE}
          </AdaptiveText>
        </Frame>

        <View style={styles.controls}>
          <Slider
            testID="FontSizeSlider"
            label="Font size"
            unit="px"
            value={fontSize}
            min={12}
            max={36}
            onChange={setFontSize}
          />
          <Slider
            label="Letter spacing"
            unit="px"
            value={letterSpacing}
            min={-1}
            max={4}
            step={0.5}
            onChange={setLetterSpacing}
          />
          <Slider
            label="Line height"
            unit="px"
            value={lineHeight}
            min={16}
            max={48}
            onChange={setLineHeight}
          />

          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Weight</Text>
            <View style={styles.row}>
              {WEIGHTS.map((w) => (
                <Pill
                  key={w}
                  label={w}
                  onPress={() => setWeight(w)}
                  selected={weight === w}
                />
              ))}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Style</Text>
            <View style={styles.row}>
              {STYLES.map((s) => (
                <Pill
                  key={s}
                  label={s}
                  onPress={() => setFontStyle(s)}
                  selected={fontStyle === s}
                />
              ))}
            </View>
          </View>

          <View style={styles.section}>
            <Text style={styles.sectionLabel}>Color</Text>
            <View style={styles.row}>
              {COLOR_OPTIONS.map((c) => (
                <Pill
                  key={c.id}
                  label={c.label}
                  onPress={() => setColorId(c.id)}
                  selected={colorId === c.id}
                />
              ))}
            </View>
          </View>
        </View>
      </ScrollView>
    </Stage>
  );
}

const styles = StyleSheet.create({
  scrollContent: {
    paddingBottom: 24,
  },
  controls: {
    gap: 14,
    marginTop: 16,
  },
  text: {
    color: '#0e1116',
  },
  section: {
    gap: 6,
  },
  sectionLabel: {
    fontSize: 12,
    fontWeight: '700',
    color: '#475569',
    letterSpacing: 0.6,
    textTransform: 'uppercase',
  },
  row: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
});

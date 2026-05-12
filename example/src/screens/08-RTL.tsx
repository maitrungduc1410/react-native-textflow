import { useState } from 'react';
import { StyleSheet, View } from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';
import { Frame } from '../components/Frame';

const SAMPLES = {
  english:
    'When there is no more space for some words, those words fly to the next line.',
  arabic:
    'عندما لا يكون هناك مساحة كافية لبعض الكلمات، تنتقل تلك الكلمات بسلاسة إلى السطر التالي.',
  hebrew:
    'כאשר אין מספיק מקום למילים מסוימות, אותן מילים זורמות בחלקלקות לשורה הבאה.',
} as const;

type Lang = keyof typeof SAMPLES;

const ALIGNMENTS = ['start', 'end', 'left', 'right', 'center'] as const;
type Align = (typeof ALIGNMENTS)[number];

export default function RTLScreen() {
  const [lang, setLang] = useState<Lang>('english');
  const [align, setAlign] = useState<Align>('start');

  return (
    <Stage caption="Switch to Arabic or Hebrew. Combine with textAlign to see start vs end behaviour.">
      <View style={styles.row}>
        {(Object.keys(SAMPLES) as Lang[]).map((k) => (
          <Pill
            key={k}
            label={k}
            onPress={() => setLang(k)}
            selected={lang === k}
          />
        ))}
      </View>
      <View style={styles.row}>
        {ALIGNMENTS.map((a) => (
          <Pill
            key={a}
            label={a}
            onPress={() => setAlign(a)}
            selected={align === a}
          />
        ))}
      </View>
      <Frame>
        <AdaptiveText style={[styles.text, { textAlign: align }]}>
          {SAMPLES[lang]}
        </AdaptiveText>
      </Frame>
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
    fontWeight: '500',
    color: '#0e1116',
    lineHeight: 30,
  },
});

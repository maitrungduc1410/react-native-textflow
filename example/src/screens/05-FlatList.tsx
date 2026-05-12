import { useCallback, useState } from 'react';
import {
  FlatList,
  StyleSheet,
  Text,
  View,
  type ListRenderItemInfo,
} from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Pill } from '../components/Pill';

type Item = { id: string; author: string; bio: string };

const ITEMS: Item[] = [
  {
    id: '1',
    author: 'Ada Lovelace',
    bio: 'The first programmer; collaborated with Charles Babbage on the analytical engine and authored the earliest published algorithm.',
  },
  {
    id: '2',
    author: 'Grace Hopper',
    bio: 'Coined the term debugging after literally extracting a moth from the Mark II; pioneer of compiler design and COBOL.',
  },
  {
    id: '3',
    author: 'Margaret Hamilton',
    bio: 'Led the team that built the on-board flight software for the Apollo missions; helped shape the very idea of software engineering.',
  },
  {
    id: '4',
    author: 'Hedy Lamarr',
    bio: 'Inventor of frequency-hopping spread spectrum, the radio technique underpinning Wi-Fi, Bluetooth, and GPS.',
  },
  {
    id: '5',
    author: 'Karen Spärck Jones',
    bio: 'Introduced inverse document frequency (idf), the cornerstone of modern information retrieval and search engines.',
  },
];

export default function FlatListScreen() {
  const [size, setSize] = useState<'compact' | 'wide'>('wide');
  const fontSize = size === 'compact' ? 14 : 16;

  const renderItem = useCallback(
    ({ item }: ListRenderItemInfo<Item>) => (
      <Row item={item} fontSize={fontSize} />
    ),
    [fontSize]
  );

  return (
    <Stage caption="Each row is its own AdaptiveText. Tap to verify rows re-measure correctly.">
      <View style={styles.toolbar}>
        <Pill
          label="compact"
          onPress={() => setSize('compact')}
          selected={size === 'compact'}
        />
        <Pill
          label="wide"
          onPress={() => setSize('wide')}
          selected={size === 'wide'}
        />
      </View>
      <FlatList
        data={ITEMS}
        keyExtractor={(item) => item.id}
        contentContainerStyle={styles.content}
        ItemSeparatorComponent={Separator}
        renderItem={renderItem}
      />
    </Stage>
  );
}

function Separator() {
  return <View style={styles.separator} />;
}

function Row({ item, fontSize }: { item: Item; fontSize: number }) {
  return (
    <View style={styles.row}>
      <Text style={styles.author}>{item.author}</Text>
      <AdaptiveText
        style={[styles.bio, { fontSize, lineHeight: fontSize * 1.45 }]}
      >
        {item.bio}
      </AdaptiveText>
    </View>
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
  },
  row: {
    backgroundColor: '#ffffff',
    borderRadius: 12,
    padding: 16,
    borderWidth: 1,
    borderColor: '#e6e9ee',
    gap: 8,
  },
  separator: {
    height: 8,
  },
  author: {
    fontSize: 13,
    fontWeight: '700',
    color: '#0f172a',
    letterSpacing: 0.4,
  },
  bio: {
    color: '#1f2937',
  },
});

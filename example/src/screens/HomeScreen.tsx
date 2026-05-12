import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { ScrollView } from 'react-native';
import { demos, type RootStackParamList } from './index';

type Nav = NativeStackNavigationProp<RootStackParamList, 'Home'>;

export default function HomeScreen() {
  const navigation = useNavigation<Nav>();

  return (
    <ScrollView contentContainerStyle={styles.content}>
      <Text style={styles.titleHero}>Adaptive Text</Text>
      <Text style={styles.subtitleHero}>
        Native SwiftUI / Jetpack Compose-style fluid text reflow.
      </Text>
      <View style={styles.list}>
        {demos.map((demo, idx) => (
          <Pressable
            key={demo.name}
            onPress={() =>
              navigation.navigate(
                demo.name as keyof RootStackParamList as never
              )
            }
            style={({ pressed }) => [styles.row, pressed && styles.rowPressed]}
          >
            <Text style={styles.rowIndex}>
              {String(idx + 1).padStart(2, '0')}
            </Text>
            <View style={styles.rowText}>
              <Text style={styles.rowTitle}>{demo.title}</Text>
              <Text style={styles.rowSubtitle}>{demo.subtitle}</Text>
            </View>
            <Text style={styles.chevron}>{'›'}</Text>
          </Pressable>
        ))}
      </View>
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  content: {
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 32,
  },
  titleHero: {
    fontSize: 32,
    fontWeight: '800',
    color: '#0e1116',
    marginBottom: 4,
  },
  subtitleHero: {
    fontSize: 15,
    color: '#52606d',
    marginBottom: 20,
  },
  list: {
    gap: 10,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#ffffff',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 12,
    borderWidth: 1,
    borderColor: '#e6e9ee',
    gap: 12,
  },
  rowPressed: {
    backgroundColor: '#eef2f6',
  },
  rowIndex: {
    fontSize: 13,
    fontWeight: '600',
    color: '#94a3b8',
    width: 24,
  },
  rowText: {
    flex: 1,
  },
  rowTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#0e1116',
    marginBottom: 2,
  },
  rowSubtitle: {
    fontSize: 13,
    color: '#5e6772',
  },
  chevron: {
    fontSize: 22,
    color: '#94a3b8',
    fontWeight: '400',
  },
});

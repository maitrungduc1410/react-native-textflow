import type { ReactNode } from 'react';
import { StyleSheet, Text, View } from 'react-native';

type Props = {
  caption?: string;
  children: ReactNode;
};

/**
 * Shared frame for every demo screen. The navigator header now owns the
 * title, so this component just renders an optional caption above the
 * content area and applies consistent padding.
 */
export function Stage({ caption, children }: Props) {
  return (
    <View style={styles.container}>
      {caption ? <Text style={styles.caption}>{caption}</Text> : null}
      <View style={styles.body}>{children}</View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    paddingHorizontal: 20,
    paddingTop: 12,
    paddingBottom: 24,
    gap: 12,
  },
  caption: {
    fontSize: 14,
    color: '#5e6772',
    lineHeight: 20,
  },
  body: {
    flex: 1,
  },
});

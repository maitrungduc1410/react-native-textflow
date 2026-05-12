import type { ReactNode } from 'react';
import { StyleSheet, View } from 'react-native';

type Props = {
  width?: number | `${number}%`;
  children: ReactNode;
  highlight?: boolean;
};

/**
 * Bordered, padded box used to make container boundaries obvious in demos.
 * The borders make it visually clear when text is reflowing because the
 * container changed size.
 */
export function Frame({ width, children, highlight }: Props) {
  return (
    <View
      style={[
        styles.frame,
        highlight && styles.frameHighlight,
        width !== undefined && { width: width as never },
      ]}
    >
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  frame: {
    borderRadius: 12,
    backgroundColor: '#ffffff',
    borderWidth: 1,
    borderColor: '#e6e9ee',
    padding: 16,
  },
  frameHighlight: {
    borderColor: '#3b82f6',
    backgroundColor: '#eff6ff',
  },
});

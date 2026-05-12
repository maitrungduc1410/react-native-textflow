import { useState } from 'react';
import {
  Modal,
  Pressable,
  StyleSheet,
  Text,
  useWindowDimensions,
  View,
} from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';

const PARAGRAPH =
  'Modals re-layout their content when the keyboard appears, the device rotates, or the user resizes a Stage Manager window. Adaptive text handles every one of those without you lifting a finger.';

export default function ModalScreen() {
  const [open, setOpen] = useState(false);
  const { width } = useWindowDimensions();
  const isCompact = width < 500;

  return (
    <Stage caption="Open the modal. Rotate or resize the device — text reflows live.">
      <View style={styles.center}>
        <Pressable
          onPress={() => setOpen(true)}
          style={({ pressed }) => [
            styles.openButton,
            pressed && styles.pressed,
          ]}
        >
          <Text style={styles.openLabel}>Open modal</Text>
        </Pressable>
      </View>

      <Modal
        visible={open}
        animationType="fade"
        transparent
        onRequestClose={() => setOpen(false)}
      >
        <View style={styles.scrim}>
          <View style={[styles.sheet, !isCompact && styles.sheetWide]}>
            <Text style={styles.heading}>About this library</Text>
            <AdaptiveText style={styles.body}>{PARAGRAPH}</AdaptiveText>
            <Pressable
              onPress={() => setOpen(false)}
              style={({ pressed }) => [
                styles.closeButton,
                pressed && styles.pressed,
              ]}
            >
              <Text style={styles.closeLabel}>Close</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </Stage>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  openButton: {
    paddingVertical: 12,
    paddingHorizontal: 20,
    borderRadius: 12,
    backgroundColor: '#0f172a',
  },
  openLabel: {
    color: '#ffffff',
    fontWeight: '600',
    fontSize: 15,
  },
  scrim: {
    flex: 1,
    backgroundColor: 'rgba(15,23,42,0.45)',
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 24,
  },
  sheet: {
    backgroundColor: '#ffffff',
    borderRadius: 16,
    padding: 20,
    width: '100%',
    gap: 12,
  },
  sheetWide: {
    maxWidth: 540,
  },
  heading: {
    fontSize: 18,
    fontWeight: '700',
    color: '#0e1116',
  },
  body: {
    fontSize: 16,
    lineHeight: 24,
    color: '#1f2937',
  },
  closeButton: {
    alignSelf: 'flex-end',
    paddingVertical: 8,
    paddingHorizontal: 14,
    borderRadius: 8,
  },
  closeLabel: {
    fontSize: 15,
    fontWeight: '600',
    color: '#3b82f6',
  },
  pressed: {
    opacity: 0.7,
  },
});

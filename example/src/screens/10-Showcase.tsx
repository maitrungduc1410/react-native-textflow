import { useState } from 'react';
import {
  Animated,
  Easing,
  Pressable,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { useRef, useEffect } from 'react';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';

const SHORT = 'Tap the card to expand it.';
const LONG =
  'Hi, I’m Adaptive Text. I render every word as a real native view inside a SwiftUI custom Layout on iOS and a Jetpack Compose FlowRow on Android. When my container resizes — like right now — each word springs to its new line position with the OS’s native physics curves.';

export default function Showcase() {
  const [expanded, setExpanded] = useState(false);
  const widthAnim = useRef(new Animated.Value(280)).current;
  const heightAnim = useRef(new Animated.Value(120)).current;

  useEffect(() => {
    Animated.parallel([
      Animated.timing(widthAnim, {
        toValue: expanded ? 340 : 280,
        duration: 450,
        easing: Easing.bezier(0.2, 0.8, 0.2, 1),
        useNativeDriver: false,
      }),
      Animated.timing(heightAnim, {
        toValue: expanded ? 240 : 120,
        duration: 450,
        easing: Easing.bezier(0.2, 0.8, 0.2, 1),
        useNativeDriver: false,
      }),
    ]).start();
  }, [expanded, widthAnim, heightAnim]);

  return (
    <Stage caption="A composed example: avatar, name, and bio. Tap to expand — bio reflows with the card.">
      <View style={styles.center}>
        <Pressable onPress={() => setExpanded((e) => !e)}>
          <Animated.View
            style={[styles.card, { width: widthAnim, minHeight: heightAnim }]}
          >
            <View style={styles.row}>
              <View style={styles.avatar}>
                <Text style={styles.avatarLabel}>AT</Text>
              </View>
              <View style={styles.identity}>
                <Text style={styles.name}>Adaptive Text</Text>
                <Text style={styles.handle}>@react-native-textflow</Text>
              </View>
            </View>
            <AdaptiveText
              style={styles.bio}
              animation={{
                type: 'spring',
                damping: 18,
                stiffness: 220,
                mass: 1,
              }}
            >
              {expanded ? LONG : SHORT}
            </AdaptiveText>
          </Animated.View>
        </Pressable>
      </View>
    </Stage>
  );
}

const styles = StyleSheet.create({
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  card: {
    backgroundColor: '#ffffff',
    borderRadius: 20,
    padding: 18,
    borderWidth: 1,
    borderColor: '#e6e9ee',
    shadowColor: '#0f172a',
    shadowOpacity: 0.08,
    shadowOffset: { width: 0, height: 4 },
    shadowRadius: 12,
    elevation: 4,
    gap: 12,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  avatar: {
    width: 48,
    height: 48,
    borderRadius: 24,
    backgroundColor: '#0f172a',
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarLabel: {
    color: '#ffffff',
    fontSize: 16,
    fontWeight: '700',
  },
  identity: {
    flex: 1,
  },
  name: {
    fontSize: 16,
    fontWeight: '700',
    color: '#0e1116',
  },
  handle: {
    fontSize: 12,
    color: '#5e6772',
  },
  bio: {
    fontSize: 16,
    lineHeight: 24,
    color: '#1f2937',
  },
});

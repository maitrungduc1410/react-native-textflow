import { useEffect, useRef, useState } from 'react';
import {
  PanResponder,
  StyleSheet,
  Text,
  View,
  type GestureResponderEvent,
  type PanResponderGestureState,
} from 'react-native';
import { AdaptiveText } from 'react-native-textflow';
import { Stage } from '../components/Stage';
import { Frame } from '../components/Frame';

const SAMPLE =
  'When there is no more space for some words, those words smoothly fly to the next line — this is the magic of native flow layout.';

const MIN_WIDTH = 120;
const MAX_PADDING = 20;

export default function ResizableContainer() {
  const [width, setWidth] = useState<number>(280);
  const [available, setAvailable] = useState<number>(360);

  const widthRef = useRef(width);
  const availableRef = useRef(available);
  const startWidth = useRef(width);
  useEffect(() => {
    widthRef.current = width;
    availableRef.current = available;
  });

  const responder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderTerminationRequest: () => false,
      onPanResponderGrant: () => {
        startWidth.current = widthRef.current;
      },
      onPanResponderMove: (
        _e: GestureResponderEvent,
        g: PanResponderGestureState
      ) => {
        const next = Math.max(
          MIN_WIDTH,
          Math.min(availableRef.current, startWidth.current + g.dx)
        );
        if (next !== widthRef.current) {
          widthRef.current = next;
          setWidth(next);
        }
      },
    })
  ).current;

  return (
    <Stage caption="Drag the blue handle. Watch each word slide between lines as the available width changes.">
      <View
        style={styles.canvas}
        onLayout={(e) =>
          setAvailable(e.nativeEvent.layout.width - MAX_PADDING * 2)
        }
      >
        <Frame width={width} highlight>
          <AdaptiveText style={styles.text}>{SAMPLE}</AdaptiveText>
        </Frame>
        <View
          {...responder.panHandlers}
          style={[styles.handle, { left: width - 16 }]}
          // `accessible` is required on iOS for this plain <View> to
          // become an `isAccessibilityElement` and therefore be visible
          // to XCTest / Maestro. RN auto-sets this flag for touchables
          // but NOT for plain Views with just `accessibilityLabel` /
          // `accessibilityRole` — Android's a11y tree exposes them
          // anyway, which made this look "fine" cross-platform until
          // the iOS Maestro flow tried to match the handle by `id:`.
          //
          // Note we deliberately do NOT set `accessibilityRole="adjustable"`
          // here even though semantically it fits a drag handle. RN maps
          // that role to `UIAccessibilityTraitAdjustable`, which (a) is
          // non-functional without a paired `accessibilityValue` +
          // `onAccessibilityAction` increment/decrement handler and (b)
          // re-categorises the element in XCTest's snapshot in a way
          // that broke Maestro's `id:` query. Adding the role properly
          // — with value + actions — would be a future improvement.
          accessible
          testID="ResizeHandle"
          accessibilityLabel="Resize handle"
        >
          <View style={styles.handleGrip} />
        </View>
      </View>
      <Text style={styles.readout}>
        Width: {Math.round(width)}px / {Math.round(available)}px
      </Text>
    </Stage>
  );
}

const styles = StyleSheet.create({
  canvas: {
    flex: 1,
    paddingHorizontal: MAX_PADDING,
    paddingTop: 12,
    position: 'relative',
  },
  text: {
    fontSize: 18,
    lineHeight: 26,
    color: '#0e1116',
    fontWeight: '500',
  },
  handle: {
    position: 'absolute',
    top: 60,
    width: 32,
    height: 60,
    alignItems: 'center',
    justifyContent: 'center',
  },
  handleGrip: {
    width: 6,
    height: 36,
    borderRadius: 3,
    backgroundColor: '#3b82f6',
  },
  readout: {
    fontSize: 13,
    color: '#5e6772',
    fontVariant: ['tabular-nums'],
    marginTop: 12,
    paddingHorizontal: MAX_PADDING,
  },
});

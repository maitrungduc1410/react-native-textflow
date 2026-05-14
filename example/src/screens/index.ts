import type { ComponentType } from 'react';
import type { NativeStackNavigationOptions } from '@react-navigation/native-stack';
import ResizableContainer from './01-ResizableContainer';
import AnimatedWidth from './02-AnimatedWidth';
import ModalScreen from './03-Modal';
import ScrollViewScreen from './04-ScrollView';
import FlatListScreen from './05-FlatList';
import DynamicContent from './06-DynamicContent';
import StyleMorph from './07-StyleMorph';
import RTLScreen from './08-RTL';
import AnimationConfig from './09-AnimationConfig';
import Showcase from './10-Showcase';
import ThemeScreen from './11-Theme';

/**
 * One row in the home screen. The `name` is also the route name used by
 * React Navigation, so be sure not to rename without updating
 * `RootStackParamList` below.
 *
 * `options` is an optional per-screen override merged with the
 * navigator's defaults in App.tsx. Use it sparingly — most screens
 * should rely on the global `screenOptions`.
 */
export type DemoRoute = {
  name: string;
  title: string;
  subtitle: string;
  component: ComponentType;
  options?: NativeStackNavigationOptions;
};

export const demos: DemoRoute[] = [
  {
    name: 'ResizableContainer',
    title: 'Resizable container',
    subtitle:
      'Drag the handle. Words spring between lines as the width changes.',
    component: ResizableContainer,
    // Disable iOS's interactive-pop gesture on this screen. The screen's
    // primary interaction is a horizontal drag on the resize handle —
    // Maestro's synthesised RIGHT-direction swipe (and, in some cases,
    // a real user dragging the handle far enough) competes with
    // UINavigationController's `interactivePopGestureRecognizer` and
    // pops the screen back to Home mid-test. Real users still have the
    // back button + back tap target in the header. No effect on Android,
    // where there's no built-in horizontal-pop gesture.
    options: { gestureEnabled: false },
  },
  {
    name: 'AnimatedWidth',
    title: 'Animated width',
    subtitle: 'Tap to toggle the container width. Reflow stays in sync.',
    component: AnimatedWidth,
  },
  {
    name: 'Modal',
    title: 'Inside a Modal',
    subtitle: 'Adaptive text reflows inside RN’s built-in <Modal>.',
    component: ModalScreen,
  },
  {
    name: 'ScrollView',
    title: 'Inside a ScrollView',
    subtitle: 'Long-form paragraphs in a vertical ScrollView.',
    component: ScrollViewScreen,
  },
  {
    name: 'FlatList',
    title: 'Inside a FlatList',
    subtitle:
      'Each row is an AdaptiveText. Verifies measurement in virtualization.',
    component: FlatListScreen,
  },
  {
    name: 'DynamicContent',
    title: 'Dynamic content',
    subtitle:
      'Add, remove, or replace words. Survivors slide; new ones fade in.',
    component: DynamicContent,
  },
  {
    name: 'StyleMorph',
    title: 'Style morphing',
    subtitle:
      'Slide font size and letter spacing — text reflows on every frame.',
    component: StyleMorph,
  },
  {
    name: 'RTL',
    title: 'RTL',
    subtitle: 'Right-to-left content flows correctly.',
    component: RTLScreen,
  },
  {
    name: 'AnimationConfig',
    title: 'Animation config',
    subtitle: 'Switch between spring, timing, and none — feel the curves.',
    component: AnimationConfig,
  },
  {
    name: 'Showcase',
    title: 'Showcase card',
    subtitle: 'A composed demo: avatar + adaptive bio that grows on tap.',
    component: Showcase,
  },
  {
    name: 'Theme',
    title: 'Dark / Light theme',
    subtitle: 'Card colors interpolate; AdaptiveText follows the active theme.',
    component: ThemeScreen,
  },
];

/** Route param map. Every demo takes no params. */
export type RootStackParamList = {
  Home: undefined;
} & Record<(typeof demos)[number]['name'], undefined>;

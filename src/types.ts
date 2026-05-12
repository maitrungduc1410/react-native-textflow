import type {
  ColorValue,
  StyleProp,
  TextStyle,
  ViewProps,
  ViewStyle,
} from 'react-native';

export type AdaptiveSplitMode = 'word' | 'grapheme';

export type AdaptiveEasing = 'linear' | 'easeIn' | 'easeOut' | 'easeInOut';

export type AdaptiveTextAlign =
  | 'auto'
  | 'left'
  | 'right'
  | 'center'
  | 'start'
  | 'end';

export type AdaptiveSpringAnimation = {
  type: 'spring';
  /** Default 18. Higher = less oscillation. */
  damping?: number;
  /** Default 220. Higher = snappier. */
  stiffness?: number;
  /** Default 1. Higher = heavier feel. */
  mass?: number;
};

export type AdaptiveTimingAnimation = {
  type: 'timing';
  /** Default 250 (ms). */
  duration?: number;
  /** Default `'easeInOut'`. */
  easing?: AdaptiveEasing;
};

export type AdaptiveNoneAnimation = {
  type: 'none';
};

export type AdaptiveAnimationConfig =
  | AdaptiveSpringAnimation
  | AdaptiveTimingAnimation
  | AdaptiveNoneAnimation;

/**
 * Public props of the `<AdaptiveText>` component.
 *
 * Forwards a subset of `TextStyle` (font, color, lineHeight, letterSpacing,
 * textAlign) to the native side as discrete props, so the OS-level layout
 * engine can shape and animate text correctly.
 */
export type AdaptiveTextProps = Omit<ViewProps, 'children'> & {
  /**
   * Text content. Either pass it as JSX children or via the `text` prop.
   * Children win when both are provided.
   */
  children?: string | number;
  /** Alternative to `children`. */
  text?: string;
  /**
   * Standard React Native style. Text-specific keys (`fontSize`, `fontFamily`,
   * `fontWeight`, `fontStyle`, `color`, `letterSpacing`, `lineHeight`,
   * `textAlign`) are forwarded to the native flow layout. All other keys
   * (size, padding, background, etc.) style the wrapper view.
   */
  style?: StyleProp<ViewStyle | TextStyle>;
  /**
   * Tokenization granularity.
   *
   * - `'word'` (default): split on whitespace; each word is animated independently.
   * - `'grapheme'`: each user-perceived character animates independently.
   *   Useful for CJK or "typewriter" effects.
   */
  splitBy?: AdaptiveSplitMode;
  /**
   * Animation curve used when the layout reflows (container resize, text
   * change, style change). Defaults to a spring.
   */
  animation?: AdaptiveAnimationConfig;
  /**
   * Horizontal spacing between tokens (in points). Defaults to the native
   * font's natural space width when 0.
   */
  wordSpacing?: number;
  /**
   * Vertical spacing between lines (in points). Defaults to 0 (native uses
   * the font's natural line height).
   */
  lineSpacing?: number;
  /** Override colour on the text only (separate from `style.backgroundColor`). */
  textColor?: ColorValue;
};

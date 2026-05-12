import { Children } from 'react';
import { StyleSheet, type TextStyle } from 'react-native';

import NativeAdaptiveText from './AdaptiveTextNativeComponent';
import type { AdaptiveTextProps } from './types';

/**
 * Style keys that we forward to the native flow layout as discrete props.
 * Anything else (size, padding, background, etc.) styles the wrapper view.
 */
const TEXT_STYLE_KEYS = [
  'fontSize',
  'fontFamily',
  'fontWeight',
  'fontStyle',
  'color',
  'letterSpacing',
  'lineHeight',
  'textAlign',
] as const;

function splitStyle(style: AdaptiveTextProps['style']): {
  textStyle: Partial<TextStyle>;
  viewStyle: AdaptiveTextProps['style'];
} {
  const flat = (StyleSheet.flatten(style) ?? {}) as Record<string, unknown>;
  const textStyle: Record<string, unknown> = {};
  const viewStyle: Record<string, unknown> = {};
  for (const key of Object.keys(flat)) {
    if ((TEXT_STYLE_KEYS as readonly string[]).includes(key)) {
      textStyle[key] = flat[key];
    } else {
      viewStyle[key] = flat[key];
    }
  }
  return {
    textStyle: textStyle as Partial<TextStyle>,
    viewStyle: viewStyle as AdaptiveTextProps['style'],
  };
}

function coerceText(
  children: AdaptiveTextProps['children'],
  text: string | undefined
): string {
  if (children !== undefined && children !== null) {
    if (typeof children === 'string') return children;
    if (typeof children === 'number') return String(children);
    // Concatenate string fragments if a JSX expression like `{a} {b}` was used.
    const flattened = Children.toArray(children)
      .map((c) =>
        typeof c === 'string' || typeof c === 'number' ? String(c) : ''
      )
      .join('');
    if (flattened.length > 0) return flattened;
  }
  return text ?? '';
}

/**
 * `<AdaptiveText>` renders fluidly-reflowing text whose words spring between
 * lines whenever the container resizes. Layout and motion are handled
 * natively (SwiftUI custom Layout on iOS, Jetpack Compose FlowRow with
 * `Modifier.animateBounds` on Android), so the look matches the host OS.
 *
 * @example
 * ```tsx
 * <AdaptiveText
 *   style={{ fontSize: 24, fontWeight: '600', color: '#111' }}
 *   animation={{ type: 'spring', damping: 18, stiffness: 220 }}
 * >
 *   When there is no more space for some words, those words smoothly fly to the next line.
 * </AdaptiveText>
 * ```
 */
export function AdaptiveText(props: AdaptiveTextProps) {
  const {
    children,
    text,
    style,
    splitBy = 'word',
    animation,
    wordSpacing,
    lineSpacing,
    textColor,
    accessibilityLabel,
    ...rest
  } = props;

  const value = coerceText(children, text);
  const { textStyle, viewStyle } = splitStyle(style);

  return (
    <NativeAdaptiveText
      {...rest}
      style={viewStyle}
      text={value}
      splitBy={splitBy}
      fontSize={
        typeof textStyle.fontSize === 'number' ? textStyle.fontSize : undefined
      }
      fontFamily={textStyle.fontFamily}
      fontWeight={normalizeFontWeight(textStyle.fontWeight)}
      fontStyle={textStyle.fontStyle}
      textColor={textColor ?? textStyle.color}
      letterSpacing={
        typeof textStyle.letterSpacing === 'number'
          ? textStyle.letterSpacing
          : undefined
      }
      lineHeight={
        typeof textStyle.lineHeight === 'number'
          ? textStyle.lineHeight
          : undefined
      }
      textAlign={normalizeTextAlign(textStyle.textAlign)}
      wordSpacing={wordSpacing}
      lineSpacing={lineSpacing}
      animation={animation}
      accessible
      accessibilityLabel={accessibilityLabel ?? value}
    />
  );
}

function normalizeTextAlign(
  align: TextStyle['textAlign']
): 'auto' | 'left' | 'right' | 'center' | 'start' | 'end' | undefined {
  if (align === undefined) return undefined;
  // RN's `textAlign` includes `'justify'` which our flow layout doesn't
  // support. Treat `justify` as `start` so callers don't see a runtime
  // mismatch.
  if (align === 'justify') return 'start';
  return align;
}

const FONT_WEIGHT_VALUES = new Set([
  'normal',
  'bold',
  '100',
  '200',
  '300',
  '400',
  '500',
  '600',
  '700',
  '800',
  '900',
]);

function normalizeFontWeight(
  weight: TextStyle['fontWeight']
):
  | 'normal'
  | 'bold'
  | '100'
  | '200'
  | '300'
  | '400'
  | '500'
  | '600'
  | '700'
  | '800'
  | '900'
  | undefined {
  if (weight === undefined) return undefined;
  const stringified = typeof weight === 'number' ? String(weight) : weight;
  if (FONT_WEIGHT_VALUES.has(stringified)) {
    return stringified as 'normal';
  }
  return undefined;
}

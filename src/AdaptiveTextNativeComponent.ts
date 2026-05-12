import { codegenNativeComponent } from 'react-native';
import type {
  Float,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { ColorValue, ViewProps } from 'react-native';

type SplitMode = 'word' | 'grapheme';
type AnimationType = 'spring' | 'timing' | 'none';
type Easing = 'linear' | 'easeIn' | 'easeOut' | 'easeInOut';
type TextAlign = 'auto' | 'left' | 'right' | 'center' | 'start' | 'end';
type FontStyle = 'normal' | 'italic';

/**
 * Animation config forwarded to native. Numeric fields default to 0 in
 * codegen output; the native side substitutes its own sensible defaults
 * when 0 is observed.
 */
type AnimationConfig = Readonly<{
  type?: WithDefault<AnimationType, 'spring'>;
  damping?: Float;
  stiffness?: Float;
  mass?: Float;
  duration?: Float;
  easing?: WithDefault<Easing, 'easeInOut'>;
}>;

interface NativeProps extends ViewProps {
  text?: string;
  splitBy?: WithDefault<SplitMode, 'word'>;

  fontSize?: Float;
  fontFamily?: string;
  // Codegen can't generate a clean C++ enum for numeric weights ('100'..'900')
  // because identifiers cannot start with digits. We accept a free-form string
  // here and parse it on the native side.
  fontWeight?: string;
  fontStyle?: WithDefault<FontStyle, 'normal'>;
  textColor?: ColorValue;
  letterSpacing?: Float;
  lineHeight?: Float;
  textAlign?: WithDefault<TextAlign, 'start'>;

  wordSpacing?: Float;
  lineSpacing?: Float;

  animation?: AnimationConfig;
}

export default codegenNativeComponent<NativeProps>('AdaptiveTextView');

/**
 * Unit tests for the JS facade in `src/AdaptiveText.tsx`.
 *
 * The native Fabric component (`AdaptiveTextNativeComponent`) is mocked
 * with a tiny host component that records the props it was rendered
 * with, so each test can assert on exactly what would have been
 * forwarded to the C++ shadow node / native view.
 *
 * What we cover here:
 *   - splitStyle: text-style keys are extracted as discrete props,
 *     non-text-style keys remain on `style`.
 *   - normalizeFontWeight: number weights are stringified, unknown
 *     weights become `undefined`, named weights pass through.
 *   - normalizeTextAlign: `'justify'` falls back to `'start'` (our flow
 *     layout doesn't support justify; see comment in AdaptiveText.tsx).
 *   - coerceText: children win over `text`; numbers coerce to string;
 *     fragments concatenate; empty collapses to `''`.
 *   - accessibilityLabel: defaults to the rendered text when not set.
 *   - The §4.3 trap from AGENTS.md: rendering `<AdaptiveText>` with an
 *     interpolated Animated `style.color` must not throw at the JSX/JS
 *     layer. (The actual Animated-vs-Fabric warning lives at the native
 *     bridge boundary; the test here only guards the JS render path.)
 */
import { Animated } from 'react-native';
import { render } from '@testing-library/react-native';

import { AdaptiveText } from '../AdaptiveText';

const propsLog: Record<string, unknown>[] = [];

jest.mock('../AdaptiveTextNativeComponent', () => {
  const ReactInner = require('react') as typeof import('react');
  return {
    __esModule: true,
    default: function MockNativeAdaptiveText(props: Record<string, unknown>) {
      propsLog.push({ ...props });
      return ReactInner.createElement(
        'AdaptiveTextView',
        props,

        (props as any).children
      );
    },
  };
});

const lastProps = (): Record<string, unknown> => {
  if (propsLog.length === 0) {
    throw new Error('No props recorded by mock native component yet.');
  }
  return propsLog[propsLog.length - 1]!;
};

beforeEach(() => {
  propsLog.length = 0;
});

describe('AdaptiveText / splitStyle', () => {
  it('forwards text-style keys as discrete native props and keeps non-text keys on style', () => {
    render(
      <AdaptiveText
        style={{
          fontSize: 18,
          fontFamily: 'Inter',
          fontWeight: '600',
          fontStyle: 'italic',
          color: '#abcdef',
          letterSpacing: 0.5,
          lineHeight: 24,
          textAlign: 'center',
          padding: 12,
          backgroundColor: '#000',
          borderRadius: 4,
        }}
      >
        Hello
      </AdaptiveText>
    );
    const p = lastProps();
    expect(p.fontSize).toBe(18);
    expect(p.fontFamily).toBe('Inter');
    expect(p.fontWeight).toBe('600');
    expect(p.fontStyle).toBe('italic');
    expect(p.textColor).toBe('#abcdef');
    expect(p.letterSpacing).toBe(0.5);
    expect(p.lineHeight).toBe(24);
    expect(p.textAlign).toBe('center');
    expect(p.style).toMatchObject({
      padding: 12,
      backgroundColor: '#000',
      borderRadius: 4,
    });
    expect(p.style).not.toHaveProperty('fontSize');
    expect(p.style).not.toHaveProperty('color');
    expect(p.style).not.toHaveProperty('textAlign');
  });

  it('uses the explicit textColor prop when provided, even if style.color is also set', () => {
    render(
      <AdaptiveText style={{ color: '#111' }} textColor="#abc">
        Hello
      </AdaptiveText>
    );
    expect(lastProps().textColor).toBe('#abc');
  });

  it('flattens an array style', () => {
    render(
      <AdaptiveText style={[{ fontSize: 14 }, { fontSize: 22, padding: 8 }]}>
        Hi
      </AdaptiveText>
    );
    const p = lastProps();
    expect(p.fontSize).toBe(22);
    expect(p.style).toMatchObject({ padding: 8 });
  });

  it('omits non-numeric fontSize / lineHeight / letterSpacing rather than forwarding garbage', () => {
    render(
      <AdaptiveText
        style={{ fontSize: 'huge' as any, lineHeight: '24' as any }}
      >
        Hi
      </AdaptiveText>
    );
    const p = lastProps();
    expect(p.fontSize).toBeUndefined();
    expect(p.lineHeight).toBeUndefined();
  });
});

describe('AdaptiveText / normalizeFontWeight', () => {
  const cases: Array<[unknown, string | undefined]> = [
    ['normal', 'normal'],
    ['bold', 'bold'],
    ['600', '600'],
    [600, '600'],
    [400, '400'],
    [undefined, undefined],
    ['ultra-heavy', undefined],
    [123, undefined],
  ];

  it.each(cases)('weight %p -> %p', (input, expected) => {
    render(
      <AdaptiveText
        style={input === undefined ? {} : ({ fontWeight: input as any } as any)}
      >
        x
      </AdaptiveText>
    );
    expect(lastProps().fontWeight).toBe(expected);
  });
});

describe('AdaptiveText / normalizeTextAlign', () => {
  it("maps 'justify' to 'start' so the flow layout never sees it", () => {
    render(<AdaptiveText style={{ textAlign: 'justify' }}>x</AdaptiveText>);
    expect(lastProps().textAlign).toBe('start');
  });

  it.each(['auto', 'left', 'right', 'center'] as const)(
    "passes through '%s' unchanged",
    (align) => {
      render(<AdaptiveText style={{ textAlign: align }}>x</AdaptiveText>);
      expect(lastProps().textAlign).toBe(align);
    }
  );

  it('omits textAlign when not set', () => {
    render(<AdaptiveText>x</AdaptiveText>);
    expect(lastProps().textAlign).toBeUndefined();
  });
});

describe('AdaptiveText / coerceText', () => {
  it('uses string children when provided', () => {
    render(<AdaptiveText>Hello world</AdaptiveText>);
    expect(lastProps().text).toBe('Hello world');
  });

  it('coerces numeric children to a string', () => {
    render(<AdaptiveText>{42}</AdaptiveText>);
    expect(lastProps().text).toBe('42');
  });

  it('concatenates JSX-fragment style children of strings/numbers', () => {
    // The public `children` type narrows to `string | number`, but the
    // internal `coerceText` implementation also handles fragments via
    // `Children.toArray`. We exercise that runtime path here by casting
    // through `unknown` — see AdaptiveText.tsx coerceText().
    const element = (
      <AdaptiveText>
        {/* @ts-expect-error narrow public type vs broader runtime support */}
        {['foo', ' ', 'bar']}
      </AdaptiveText>
    );
    render(element);
    expect(lastProps().text).toBe('foo bar');
  });

  it('falls back to the `text` prop when children is empty', () => {
    render(<AdaptiveText text="from prop" />);
    expect(lastProps().text).toBe('from prop');
  });

  it('children win over the `text` prop when both are provided', () => {
    render(<AdaptiveText text="from prop">from children</AdaptiveText>);
    expect(lastProps().text).toBe('from children');
  });

  it('ends up with an empty string when neither children nor text is set', () => {
    render(<AdaptiveText />);
    expect(lastProps().text).toBe('');
  });
});

describe('AdaptiveText / accessibility', () => {
  it('derives accessibilityLabel from the rendered text by default', () => {
    render(<AdaptiveText>Hello adaptive world</AdaptiveText>);
    const p = lastProps();
    expect(p.accessibilityLabel).toBe('Hello adaptive world');
    expect(p.accessible).toBe(true);
  });

  it('respects an explicit accessibilityLabel', () => {
    render(
      <AdaptiveText accessibilityLabel="explicit">Hello world</AdaptiveText>
    );
    expect(lastProps().accessibilityLabel).toBe('explicit');
  });
});

describe('AdaptiveText / forwarded misc props', () => {
  it('forwards splitBy with default of "word" and lets caller override', () => {
    render(<AdaptiveText>x</AdaptiveText>);
    expect(lastProps().splitBy).toBe('word');

    render(<AdaptiveText splitBy="grapheme">x</AdaptiveText>);
    expect(lastProps().splitBy).toBe('grapheme');
  });

  it('forwards animation config verbatim', () => {
    const animation = { type: 'spring', damping: 18, stiffness: 220 } as const;
    render(<AdaptiveText animation={animation}>x</AdaptiveText>);
    expect(lastProps().animation).toEqual(animation);
  });

  it('forwards wordSpacing and lineSpacing', () => {
    render(
      <AdaptiveText wordSpacing={4} lineSpacing={2}>
        x
      </AdaptiveText>
    );
    const p = lastProps();
    expect(p.wordSpacing).toBe(4);
    expect(p.lineSpacing).toBe(2);
  });
});

describe('AdaptiveText / §4.3 Animated style does not crash JS render path', () => {
  // AGENTS.md §4.3: passing an Animated value into a Fabric native
  // component crashes inside RN's Animated child-tracking. This test
  // pins only the JS-side render — the actual native bridge crash
  // requires the Fabric runtime, not Jest. We just want to ensure that
  // typing `style={{ color: anim.interpolate(...) }}` doesn't blow up
  // before it ever reaches the bridge, so callers can author the JSX
  // without their tests imploding.
  it('renders without throwing', () => {
    const anim = new Animated.Value(0);
    const color = anim.interpolate({
      inputRange: [0, 1],
      outputRange: ['#000', '#fff'],
    });
    expect(() => {
      render(
        <AdaptiveText style={{ color: color as any }}>animated</AdaptiveText>
      );
    }).not.toThrow();
  });
});

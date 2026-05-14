/**
 * Jest config for the library root.
 *
 * - `@react-native/jest-preset` provides the RN-specific transformer set,
 *   module mappers and the global mocks (NativeModules, Animated, etc.) we
 *   need to render `<AdaptiveText>` without booting the native runtime.
 * - The example workspace has its own `example/jest.config.js`; we ignore
 *   it from here so a `jest` invocation at the root only picks up the
 *   library's own `src/__tests__/...` files.
 * - We do *not* run the bob-built artefacts under `lib/` through Jest —
 *   testing the source TS is what we want.
 */
module.exports = {
  preset: '@react-native/jest-preset',
  testEnvironment: 'node',
  rootDir: __dirname,
  roots: ['<rootDir>/src'],
  testMatch: ['**/__tests__/**/*.test.[jt]s?(x)'],
  testPathIgnorePatterns: ['/node_modules/', '/lib/', '/example/'],
  moduleFileExtensions: ['ts', 'tsx', 'js', 'jsx', 'json'],
  transformIgnorePatterns: [
    'node_modules/(?!(?:@?react-native|react-native-textflow)/)',
  ],
  collectCoverageFrom: [
    'src/**/*.{ts,tsx}',
    '!src/**/*.d.ts',
    '!src/**/__tests__/**',
    '!src/AdaptiveTextNativeComponent.ts',
  ],
};

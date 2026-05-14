import { fixupConfigRules } from '@eslint/compat';
import { FlatCompat } from '@eslint/eslintrc';
import js from '@eslint/js';
import prettier from 'eslint-plugin-prettier';
import { defineConfig } from 'eslint/config';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

export default defineConfig([
  {
    extends: fixupConfigRules(compat.extends('@react-native', 'prettier')),
    plugins: { prettier },
    rules: {
      'react/react-in-jsx-scope': 'off',
      'prettier/prettier': 'error',
    },
  },
  {
    // Gradle's `testDebugUnitTest`/`testReleaseUnitTest` tasks emit
    // an HTML report containing a generated `report.js` whose
    // formatting we do not control — and Jest's `--coverage` writes
    // an `lcov-report/` HTML tree with the same property. Both must
    // be excluded so `yarn lint` doesn't fail on output artifacts.
    ignores: [
      'node_modules/',
      'lib/',
      'android/build/',
      'example/android/build/',
      'coverage/',
      '.maestro/',
    ],
  },
]);

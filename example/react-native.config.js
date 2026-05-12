const path = require('path');
const pkg = require('../package.json');

module.exports = {
  project: {
    ios: {
      automaticPodsInstallation: true,
    },
  },
  dependencies: {
    [pkg.name]: {
      // Resolve the library from the monorepo root (../) instead of the
      // example app's own node_modules. We deliberately do NOT override
      // `platforms` here so the library-side `react-native.config.js`
      // (and in particular its custom Android `componentDescriptors` /
      // `cmakeListsPath`) is honored.
      root: path.join(__dirname, '..'),
    },
  },
};

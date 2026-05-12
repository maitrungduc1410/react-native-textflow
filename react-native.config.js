/**
 * Library-side autolinking config.
 *
 * The Android entry overrides two things that codegen would otherwise drive
 * automatically:
 *
 * 1. `componentDescriptors` — autolinking emits one
 *    `concreteComponentDescriptorProvider<X>()` call per name listed here.
 *    By advertising `AdaptiveTextComponentDescriptor` (defined in our own
 *    `common/cpp` headers) instead of the codegen default
 *    `AdaptiveTextViewComponentDescriptor`, the host app installs *our*
 *    descriptor — the one that pairs Fabric props with our custom
 *    `AdaptiveTextShadowNode` and its Yoga measure callback.
 *
 * 2. `cmakeListsPath` — points to a hand-rolled CMakeLists that compiles
 *    the codegen sources together with our custom shadow node, descriptor,
 *    and JNI measurer. Without this override autolinking would build the
 *    bare codegen library, our extra C++ sources would never be linked,
 *    and `concreteComponentDescriptorProvider<AdaptiveTextComponentDescriptor>()`
 *    would fail to compile because the symbol isn't visible to
 *    `<AdaptiveTextViewSpec.h>` (the include autolinking emits).
 *
 *    NOTE: `cmakeListsPath` is resolved by `@react-native-community/cli-config-android`
 *    as `path.join(sourceDir, userConfig.cmakeListsPath)`, where `sourceDir` is
 *    already `<library>/android`. So this string must be relative to `android/`,
 *    NOT to the library root — using `'android/src/main/jni/CMakeLists.txt'`
 *    here would produce the doubled `<library>/android/android/...` path.
 */
module.exports = {
  dependency: {
    platforms: {
      android: {
        componentDescriptors: ['AdaptiveTextComponentDescriptor'],
        cmakeListsPath: 'src/main/jni/CMakeLists.txt',
      },
    },
  },
};

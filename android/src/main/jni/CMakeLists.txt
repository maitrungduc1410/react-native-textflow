cmake_minimum_required(VERSION 3.13)

# This CMake file is referenced from `react-native.config.js` as
# `cmakeListsPath`. RN's autolinking task adds it as a subdirectory of the
# host app's main CMake target, so the resulting library is linked into the
# app together with all the codegen-generated artifacts.
#
# We deliberately reuse the same target name the codegen build would have
# produced (`react_codegen_AdaptiveTextViewSpec`), because the autolinking
# template links against that exact symbol via:
#
#   target_link_libraries(${CMAKE_PROJECT_NAME} react_codegen_AdaptiveTextViewSpec)
#
# Building it ourselves lets us bundle:
#   * the codegen sources (Props/ShadowNodes/EventEmitters/...),
#   * our custom shadow node + component descriptor (in common/cpp),
#   * the JNI bridge that hands measurement off to a Kotlin Paint-based
#     implementation (in this directory),
# all into a single .so the app already loads.

set(LIB_LITERAL AdaptiveTextViewSpec)
set(LIB_TARGET_NAME react_codegen_${LIB_LITERAL})

set(LIB_ANDROID_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../..)
set(LIB_PACKAGE_ROOT ${LIB_ANDROID_DIR}/..)
set(LIB_COMMON_DIR ${LIB_PACKAGE_ROOT}/common/cpp)
set(LIB_ANDROID_GENERATED_JNI_DIR ${LIB_ANDROID_DIR}/build/generated/source/codegen/jni)
set(LIB_ANDROID_GENERATED_COMPONENTS_DIR ${LIB_ANDROID_GENERATED_JNI_DIR}/react/renderer/components/${LIB_LITERAL})

add_compile_options(
  -fexceptions
  -frtti
  -std=c++20
  -Wall
  -Wpedantic
  -Wno-gnu-zero-variadic-macro-arguments
)

# We deliberately do NOT call `find_package(ReactAndroid)` / `find_package(fbjni)`
# here. The host app's `ReactNative-application.cmake` has already done so before
# `add_subdirectory(...)` brings us in, so the imported targets
# (`ReactAndroid::reactnative`, `ReactAndroid::jsi`, `fbjni::fbjni`) plus their
# associated variables (`ReactAndroid_VERSION_MINOR`, …) are already in scope.
# Re-running `find_package` here can collide with `add_library(... IMPORTED
# GLOBAL)` declarations from the prefab config files that don't guard against
# being included twice. Mirrors the react-native-screens / codegen pattern.

# Custom JNI bridge sources (this directory) plus the codegen-generated
# component sources. The codegen layout has:
#   - top-level `AdaptiveTextViewSpec-generated.cpp` (the TurboModule provider
#     stub, included so the symbol our shadow `AdaptiveTextViewSpec.h`
#     declares is actually defined),
#   - `react/renderer/components/AdaptiveTextViewSpec/*.cpp` (codegen
#     `Props.cpp`, `ShadowNodes.cpp`, `EventEmitters.cpp`, …).
# Glob with CONFIGURE_DEPENDS so adding a new file triggers a reconfigure.
file(GLOB LIB_CUSTOM_SRCS CONFIGURE_DEPENDS *.cpp)
file(GLOB LIB_CODEGEN_TOP_SRCS CONFIGURE_DEPENDS ${LIB_ANDROID_GENERATED_JNI_DIR}/*.cpp)
file(GLOB LIB_CODEGEN_COMPONENT_SRCS CONFIGURE_DEPENDS ${LIB_ANDROID_GENERATED_COMPONENTS_DIR}/*.cpp)

add_library(
  ${LIB_TARGET_NAME}
  SHARED
  ${LIB_CUSTOM_SRCS}
  ${LIB_CODEGEN_TOP_SRCS}
  ${LIB_CODEGEN_COMPONENT_SRCS}
)

# `.` is searched first so our `AdaptiveTextViewSpec.h` shadow header
# overrides the codegen-generated one (the codegen header would otherwise
# pull in the default `using AdaptiveTextViewComponentDescriptor = …`
# typedef instead of our class-based descriptor).
#
# `${LIB_COMMON_DIR}` lets `<react/renderer/components/AdaptiveTextViewSpec/
# AdaptiveTextShadowNode.h>` resolve to the shared header used by iOS too.
target_include_directories(
  ${LIB_TARGET_NAME}
  PUBLIC
  .
  ${LIB_COMMON_DIR}
  ${LIB_ANDROID_GENERATED_JNI_DIR}
  ${LIB_ANDROID_GENERATED_COMPONENTS_DIR}
)

target_link_libraries(
  ${LIB_TARGET_NAME}
  ReactAndroid::reactnative
  ReactAndroid::jsi
  fbjni::fbjni
  android
  log
)

if(ReactAndroid_VERSION_MINOR GREATER_EQUAL 80)
  target_compile_reactnative_options(${LIB_TARGET_NAME} PRIVATE)
else()
  target_compile_options(
    ${LIB_TARGET_NAME}
    PRIVATE
    -DLOG_TAG=\"AdaptiveText\"
    -fexceptions
    -frtti
    -std=c++20
    -Wall
  )
endif()

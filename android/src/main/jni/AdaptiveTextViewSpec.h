#pragma once

// This header *intentionally* shadows the codegen-generated
// `AdaptiveTextViewSpec.h` that lives under
// `android/build/generated/source/codegen/jni/AdaptiveTextViewSpec.h`.
//
// RN's autolinking task generates an `autolinking.cpp` in the *host app's*
// build directory that does, for every native module / component
// declared by linked libraries:
//
//     #include <AdaptiveTextViewSpec.h>
//     #include <react/renderer/components/AdaptiveTextViewSpec/ComponentDescriptors.h>
//     ...
//     providerRegistry->add(
//         concreteComponentDescriptorProvider<AdaptiveTextComponentDescriptor>());
//
// For that registration to compile, the symbol `AdaptiveTextComponentDescriptor`
// must be in scope at the point of the `#include`. Our CMakeLists puts this
// directory ahead of the codegen JNI dir on the include path, so this file
// wins the lookup and pulls in our class-based descriptor (which subclasses
// the codegen `AdaptiveTextViewShadowNode` and adds a Yoga measure callback).
//
// We still forward to the codegen TurboModule provider so libraries that
// wire `AdaptiveTextViewSpec_ModuleProvider` keep working — we don't ship a
// TurboModule today, but the autolinking template references it by name.
#include <ReactCommon/JavaTurboModule.h>
#include <ReactCommon/TurboModule.h>
#include <jsi/jsi.h>

#include <react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextComponentDescriptor.h>

namespace facebook {
namespace react {

JSI_EXPORT
std::shared_ptr<TurboModule> AdaptiveTextViewSpec_ModuleProvider(
    const std::string &moduleName,
    const JavaTurboModule::InitParams &params);

} // namespace react
} // namespace facebook

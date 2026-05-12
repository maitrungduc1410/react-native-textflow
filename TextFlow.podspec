require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

# Pod name `TextFlow` is the iOS-side brand for the `react-native-textflow`
# npm package. We keep the *Fabric component class* named `AdaptiveTextView`
# (see `codegenConfig.ios.components` in package.json) so existing native
# bindings, codegen output, and Xcode references don't need to move — the
# user-facing JS symbol is still `<AdaptiveText>` from
# `react-native-textflow`. This pod name only shows up in `pod install`
# output and the produced framework name.
Pod::Spec.new do |s|
  s.name         = "TextFlow"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  # SwiftUI's `Layout` protocol (used for the per-word flow layout) is iOS 16+.
  s.platforms    = { :ios => "16.0" }
  s.source       = { :git => "https://github.com/maitrungduc1410/react-native-textflow.git", :tag => "#{s.version}" }

  # ios/ -> Objective-C++ Fabric component view, Swift hosting view, CoreText
  # text measurer impl. iOS-private headers stay in this directory so the
  # default CocoaPods header layout works.
  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"

  s.pod_target_xcconfig = {
    "DEFINES_MODULE" => "YES",
    "SWIFT_VERSION" => "5.9",
    "CLANG_CXX_LANGUAGE_STANDARD" => "c++20",
    # `common/cpp/` is added on a per-target basis below by the subspec so
    # `<react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextX.h>`
    # resolves to our shared headers without disturbing the default
    # public/private header layout for iOS-only sources.
    "HEADER_SEARCH_PATHS" => "\"$(PODS_TARGET_SRCROOT)/common/cpp\""
  }

  # Shared C++ headers live in `common/cpp/` so iOS and Android can include
  # the same shadow node + descriptor + measurer interface. Following the
  # `react-native-screens` pattern, we declare them via `project_header_files`
  # (so CocoaPods leaves the directory layout untouched) and add
  # `common/cpp/` to `HEADER_SEARCH_PATHS`. The Android counterpart wires
  # the same directory through `android/src/main/jni/CMakeLists.txt`.
  #
  # `header_dir = "TextFlow"` namespaces the imported headers under
  # `<TextFlow/...>` for any iOS consumer that wanted to include them
  # directly. Internally we use the longer `<react/renderer/components/
  # AdaptiveTextViewSpec/...>` path that Fabric codegen produces, so this
  # is mostly cosmetic — it just keeps the framework's public header tree
  # consistent with the pod's name.
  s.subspec "common" do |ss|
    ss.source_files         = "common/cpp/**/*.h"
    ss.project_header_files = "common/cpp/**/*.h"
    ss.header_dir           = "TextFlow"
    ss.pod_target_xcconfig  = { "HEADER_SEARCH_PATHS" => "\"$(PODS_TARGET_SRCROOT)/common/cpp\"" }
  end

  install_modules_dependencies(s)
end

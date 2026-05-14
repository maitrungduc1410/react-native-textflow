#!/usr/bin/env ruby
# frozen_string_literal: true
#
# Idempotently adds an `AdaptiveTextExampleTests` XCTest unit-test target
# to `example/ios/AdaptiveTextExample.xcodeproj`.
#
# Why a script rather than hand-editing project.pbxproj:
#   The pbxproj is a UUID-rich JSON-ish blob. Hand-editing is brittle and
#   doesn't survive the next time someone opens Xcode and re-saves. The
#   `xcodeproj` ruby gem is the same library CocoaPods uses internally,
#   so it understands every section the pbxproj cares about and produces
#   a stable, idempotent diff. The gem is already on disk because
#   CocoaPods is part of `Gemfile`.
#
# What gets created:
#   * `AdaptiveTextExampleTests` PBXNativeTarget (com.apple.product-type.bundle.unit-test)
#   * Sources build phase containing:
#       - example/ios/AdaptiveTextExampleTests/AdaptiveTextTokenizerTests.swift
#       - example/ios/AdaptiveTextExampleTests/AdaptiveTextMeasurerTests.mm
#       - ios/AdaptiveTextTokenizer.swift           (compiled directly into the test bundle —
#       - ios/AdaptiveTextProps.swift                see comment in the test sources)
#   * Resources build phase containing:
#       - __fixtures__/tokenizer.json
#   * Build configurations Debug / Release with the matching Swift version,
#     deployment target and code-signing identity inherited from the host
#     app target. C++20 + a HEADER_SEARCH_PATH entry pointing at
#     `common/cpp/` so the measurer test's shared-header import resolves.
#   * A shared scheme named `AdaptiveTextExampleTests` so
#     `xcodebuild -scheme AdaptiveTextExampleTests test` works in CI.
#
# Re-running the script:
#   * Idempotent end-to-end. If the target / group / scheme already
#     exists from a previous run, we still walk through every
#     `add_*` and `configure_*` step to pick up any newly-listed
#     `TEST_SOURCES` / `LIB_SOURCES` / `TEST_RESOURCES` entries.
#     Each helper is internally idempotent (skip-if-already-present
#     on file refs, reuse-if-already-present on configs), so the
#     diff against an up-to-date project is zero.
#     This makes it safe to invoke from `pod install` post-hooks or
#     CI bootstrap steps and lets contributors add new tests just by
#     editing the `TEST_SOURCES` array and re-running.

require 'xcodeproj'
require 'pathname'

EXAMPLE_DIR = Pathname.new(__FILE__).realpath.dirname.parent
PROJECT_PATH = EXAMPLE_DIR + 'AdaptiveTextExample.xcodeproj'
WORKSPACE_ROOT = EXAMPLE_DIR.parent.parent

TEST_TARGET_NAME    = 'AdaptiveTextExampleTests'
APP_TARGET_NAME     = 'AdaptiveTextExample'
TEST_GROUP_PATH     = 'AdaptiveTextExampleTests'
TEST_BUNDLE_ID      = 'adaptivetext.example.tests'
TEST_INFO_PLIST     = 'AdaptiveTextExampleTests/Info.plist'

# Files compiled INTO the test bundle.
TEST_SOURCES = [
  'AdaptiveTextExampleTests/AdaptiveTextTokenizerTests.swift',
  # Obj-C++ XCTest for `adaptive_text::measure(...)`. Calls the C++
  # entry point declared in `common/cpp/.../AdaptiveTextMeasurer.h`;
  # the symbol resolves at link time via BUNDLE_LOADER (the host app
  # contains the implementation through the TextFlow pod). See the
  # long-form comment at the top of this file for the why.
  'AdaptiveTextExampleTests/AdaptiveTextMeasurerTests.mm',
].freeze

# Files lifted from the library iOS sources straight into the test
# target. We deliberately avoid `@testable import TextFlow` because
# CocoaPods doesn't ship the pod with `-enable-testing`, which means
# the pod-emitted module would expose `public` symbols only — and
# the tokenizer / props are `internal`. Compiling the source files
# again into the test target gives us full visibility without
# touching the library's public surface.
#
# Paths are stored on the file refs as relative-to-group with
# `sourceTree = "<group>"`. The test group's effective directory is
# `example/ios/AdaptiveTextExampleTests/`, so reaching the repo-root
# `ios/` folder takes THREE `..`s — up to `example/ios/`, up to
# `example/`, up to repo root, then into `ios/`. Earlier versions of
# this script used only two `..`s; that worked when an Xcode 15 /
# iPhone 15 simulator silently fell back to PROJECT_DIR, but Xcode 26
# is strict and reports "Build input files cannot be found:
# example/ios/AdaptiveTextTokenizer.swift" — exactly the
# misresolution the wrong path produces.
LIB_SOURCES = [
  '../../../ios/AdaptiveTextTokenizer.swift',
  '../../../ios/AdaptiveTextProps.swift',
].freeze

# Resources copied into the test bundle. Same path-from-group rule
# as LIB_SOURCES (three `..`s to reach the repo root).
TEST_RESOURCES = [
  '../../../__fixtures__/tokenizer.json',
].freeze

def main
  project = Xcodeproj::Project.open(PROJECT_PATH.to_s)

  app_target = project.targets.find { |t| t.name == APP_TARGET_NAME }
  raise "Could not find '#{APP_TARGET_NAME}' target" unless app_target

  test_target = project.targets.find { |t| t.name == TEST_TARGET_NAME }
  if test_target
    puts "[add_test_target] '#{TEST_TARGET_NAME}' already exists — " \
         "ensuring sources / resources / build settings are current."
  else
    puts "[add_test_target] Creating '#{TEST_TARGET_NAME}' target..."
    # `new_target` returns a configured PBXNativeTarget with Debug +
    # Release build configurations and the standard Sources /
    # Frameworks / Resources build phases pre-attached.
    test_target = project.new_target(
      :unit_test_bundle,
      TEST_TARGET_NAME,
      :ios,
      app_target.deployment_target
    )
  end

  configure_build_settings(test_target, app_target)
  add_sources(project, test_target)
  add_resources(project, test_target)
  add_test_target_dependency(app_target, test_target)
  ensure_scheme(project, test_target)

  project.save
  puts "[add_test_target] Done. If the target was just created, " \
       "re-run `pod install` so the Podfile test target hook attaches " \
       "the TextFlow pod's link flags."
end

# Mirrors the host app's compiler settings so the test target's
# Swift module sees the same iOS deployment target / Swift version /
# signing identity that the app does. The test bundle is hosted
# inside the app at runtime, so these MUST agree.
def configure_build_settings(test_target, app_target)
  test_target.build_configurations.each do |cfg|
    app_cfg = app_target.build_configurations.find { |c| c.name == cfg.name }
    settings = cfg.build_settings
    settings['INFOPLIST_FILE'] = TEST_INFO_PLIST
    settings['PRODUCT_BUNDLE_IDENTIFIER'] = TEST_BUNDLE_ID
    # Without an explicit PRODUCT_NAME, Xcode 26 leaves the value
    # empty (rather than falling back to TARGET_NAME like older
    # toolchains did), and both the "create bundle directory" and
    # "link" build commands produce `.xctest` — colliding on the
    # same path inside `.app/PlugIns/` and failing the build with
    # "Multiple commands produce …/.xctest". Pinning it to the
    # target name reproduces the pre-Xcode-26 default.
    settings['PRODUCT_NAME'] = '$(TARGET_NAME)'
    settings['IPHONEOS_DEPLOYMENT_TARGET'] = '16.0'
    settings['SWIFT_VERSION'] = '5.0'
    settings['LD_RUNPATH_SEARCH_PATHS'] = [
      '$(inherited)',
      '@executable_path/Frameworks',
      '@loader_path/Frameworks',
    ]
    settings['ALWAYS_EMBED_SWIFT_STANDARD_LIBRARIES'] = 'YES'
    settings['TEST_HOST'] =
      '$(BUILT_PRODUCTS_DIR)/AdaptiveTextExample.app/AdaptiveTextExample'
    settings['BUNDLE_LOADER'] = '$(TEST_HOST)'

    # AdaptiveTextMeasurerTests.mm imports the shared C++ header at
    # `<react/renderer/components/AdaptiveTextViewSpec/AdaptiveTextMeasurer.h>`.
    # Inside the TextFlow pod's xcconfig that path resolves via
    # `$(PODS_TARGET_SRCROOT)/common/cpp`, but the test target uses
    # `inherit! :search_paths` from the *host app*'s Pods xcconfig,
    # which doesn't expose the TextFlow-target-private include path.
    # Adding it explicitly here makes the import resolve without
    # changing the pod's public header layout (which would affect
    # downstream consumers). `$(SRCROOT)` for the test target is
    # `example/ios/`; `../../common/cpp` walks back to repo-root
    # `common/cpp/`. `$(inherited)` keeps the long list of pod
    # header paths the test bundle needs from its baseConfiguration.
    settings['HEADER_SEARCH_PATHS'] = [
      '$(inherited)',
      '"$(SRCROOT)/../../common/cpp"',
    ]
    # Match the rest of the project's C++ standard. Without this the
    # test target would inherit the toolchain default (which has
    # changed across Xcode versions), and the designated-init syntax
    # in `LayoutConstraints { .minimumSize = … }` requires C++20.
    settings['CLANG_CXX_LANGUAGE_STANDARD'] = 'c++20'
    settings['CLANG_CXX_LIBRARY'] = 'libc++'

    if app_cfg
      settings['DEVELOPMENT_TEAM'] = app_cfg.build_settings['DEVELOPMENT_TEAM'] if app_cfg.build_settings['DEVELOPMENT_TEAM']
      settings['CODE_SIGN_IDENTITY[sdk=iphoneos*]'] =
        app_cfg.build_settings['CODE_SIGN_IDENTITY[sdk=iphoneos*]'] || 'iPhone Developer'
    end
  end
end

def add_sources(project, test_target)
  group = project.main_group.find_subpath(TEST_GROUP_PATH, true)
  group.set_source_tree('<group>')
  group.set_path(TEST_GROUP_PATH)

  TEST_SOURCES.each do |relpath|
    file_path = relpath.sub(%r{\AAdaptiveTextExampleTests/}, '')
    next if group.files.any? { |f| f.path == file_path }
    file_ref = group.new_reference(file_path)
    test_target.add_file_references([file_ref])
  end

  LIB_SOURCES.each do |relpath|
    upsert_external_ref(
      group: group,
      build_phase: test_target.source_build_phase,
      relpath: relpath,
    )
  end
end

def add_resources(project, test_target)
  group = project.main_group.find_subpath(TEST_GROUP_PATH, true)
  TEST_RESOURCES.each do |relpath|
    upsert_external_ref(
      group: group,
      build_phase: test_target.resources_build_phase,
      relpath: relpath,
    )
  end
end

# Idempotent helper for file refs that live OUTSIDE the test group's
# directory (LIB_SOURCES + TEST_RESOURCES). Older runs of this script
# stored these with the wrong number of `..`s — fixing the constant
# alone wouldn't migrate existing projects, so we walk the build
# phase by basename and either:
#   1. update an existing ref's path in place if it points at a stale
#      location ("`../../ios/AdaptiveTextTokenizer.swift`" →
#      "`../../../ios/AdaptiveTextTokenizer.swift`"), or
#   2. create a fresh ref under the test group if no ref for this
#      basename exists yet.
# Matching by basename rather than by exact path string is what makes
# the script self-healing instead of producing duplicate refs.
def upsert_external_ref(group:, build_phase:, relpath:)
  basename = File.basename(relpath)
  existing = build_phase.files_references.find do |ref|
    ref && ref.path && File.basename(ref.path) == basename
  end
  if existing
    if existing.path != relpath
      puts "[add_test_target] Fixing path for #{basename}: " \
           "'#{existing.path}' → '#{relpath}'"
      existing.path = relpath
    end
    return
  end
  file_ref = group.new_file(relpath)
  build_phase.add_file_reference(file_ref)
end

# Without an explicit dependency Xcode can build the test bundle
# before the app it tests. With `TEST_HOST` set above this would
# manifest as a confusing "App not found" error from xctest.
def add_test_target_dependency(app_target, test_target)
  test_target.add_dependency(app_target)
end

def ensure_scheme(project, test_target)
  scheme_name = TEST_TARGET_NAME
  shared_dir = File.join(project.path, 'xcshareddata', 'xcschemes')
  scheme_path = File.join(shared_dir, "#{scheme_name}.xcscheme")
  if File.exist?(scheme_path)
    puts "[add_test_target] Scheme '#{scheme_name}' already present."
    return
  end

  scheme = Xcodeproj::XCScheme.new
  scheme.add_test_target(test_target)
  scheme.save_as(project.path, scheme_name, true)
end

main

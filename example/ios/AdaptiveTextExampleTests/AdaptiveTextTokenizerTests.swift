import Foundation
import XCTest

/// XCTest suite that pins iOS↔Android tokenizer parity (AGENTS.md §4.4).
///
/// Reads the cross-platform fixtures at `__fixtures__/tokenizer.json` (copied
/// into this test bundle as a resource by the `AdaptiveTextExampleTests`
/// target's Resources build phase) and asserts that the Swift
/// `AdaptiveTextTokenizer` produces the exact same `(id, text,
/// attachToPrevious)` triples that the Kotlin
/// `AdaptiveTextTokenizer.tokenize(...)` produces for the same input.
///
/// The same JSON file is consumed by
/// `android/src/test/.../AdaptiveTextTokenizerTest.kt`. If iOS and Android
/// drift, exactly one suite turns red and tells you which tokenizer changed.
///
/// The Swift sources under `ios/` are compiled into this test bundle
/// directly (see `example/ios/scripts/add_test_target.rb`) — we don't go
/// through the TextFlow pod because pod-emitted modules expose `internal`
/// symbols only with `-enable-testing` enabled, which CocoaPods doesn't
/// set by default. Direct compilation is the same trick used by other
/// React Native libraries (e.g. `react-native-screens`).
final class AdaptiveTextTokenizerTests: XCTestCase {

  func testTokenizerMatchesSharedFixtures() throws {
    let fixtures = try loadFixtures()
    XCTAssertGreaterThan(fixtures.count, 0, "no fixture entries loaded")

    for fixture in fixtures {
      let mode: AdaptiveTextSplitMode
      switch fixture.splitBy {
      case "word": mode = .word
      case "grapheme": mode = .grapheme
      default:
        XCTFail("Unknown splitBy '\(fixture.splitBy)' in fixture '\(fixture.name)'")
        continue
      }

      let actual = AdaptiveTextTokenizer.tokenize(fixture.input, mode: mode)
      XCTAssertEqual(
        actual.count,
        fixture.expected.count,
        "Token count mismatch for fixture '\(fixture.name)' " +
          "(input: \(String(reflecting: fixture.input)))"
      )

      let pairCount = min(actual.count, fixture.expected.count)
      for i in 0..<pairCount {
        let a = actual[i]
        let e = fixture.expected[i]
        XCTAssertEqual(
          a.id, e.id,
          "id[\(i)] mismatch for fixture '\(fixture.name)'"
        )
        XCTAssertEqual(
          a.text, e.text,
          "text[\(i)] mismatch for fixture '\(fixture.name)'"
        )
        XCTAssertEqual(
          a.attachToPrevious, e.attachToPrevious,
          "attachToPrevious[\(i)] mismatch for fixture '\(fixture.name)'"
        )
      }
    }
  }

  // MARK: - Fixture loading

  /// Mirrors `__fixtures__/tokenizer.json`. Kept private to this test
  /// file — the test bundle has no reason to expose this shape to anyone
  /// else, and it lets us decode without dragging Codable into the
  /// public iOS sources.
  private struct ExpectedToken: Decodable {
    let id: String
    let text: String
    let attachToPrevious: Bool
  }

  private struct Fixture: Decodable {
    let name: String
    let input: String
    let splitBy: String
    let expected: [ExpectedToken]
  }

  private func loadFixtures() throws -> [Fixture] {
    let bundle = Bundle(for: type(of: self))
    guard let url = bundle.url(forResource: "tokenizer", withExtension: "json") else {
      XCTFail(
        "tokenizer.json not present in test bundle. Verify the " +
          "AdaptiveTextExampleTests target's Resources build phase still " +
          "includes __fixtures__/tokenizer.json (see " +
          "example/ios/scripts/add_test_target.rb)."
      )
      return []
    }
    let data = try Data(contentsOf: url)
    return try JSONDecoder().decode([Fixture].self, from: data)
  }
}

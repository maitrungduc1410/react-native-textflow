import Foundation

/// A single token in the rendered flow. Tokens have stable identities so
/// SwiftUI knows which subview moved between layout passes (which is what
/// makes individual words animate to new line positions).
struct AdaptiveTextToken: Identifiable, Equatable {
  let id: String
  let text: String
  /// Whether this token must remain attached to the previous one (no
  /// inter-token spacing). Used so trailing punctuation like `.` or `,`
  /// doesn't drift away from its word when the line wraps.
  let attachToPrevious: Bool
}

enum AdaptiveTextTokenizer {
  /// Splits `text` into tokens based on `mode`. The returned IDs are stable
  /// within a tokenization pass: identical strings at the same position
  /// produce the same ID, so SwiftUI treats them as the same logical view
  /// across renders and animates frame deltas.
  static func tokenize(_ text: String, mode: AdaptiveTextSplitMode) -> [AdaptiveTextToken] {
    switch mode {
    case .word:
      return tokenizeWords(text)
    case .grapheme:
      return tokenizeGraphemes(text)
    }
  }

  private static func tokenizeWords(_ text: String) -> [AdaptiveTextToken] {
    guard !text.isEmpty else { return [] }
    var tokens: [AdaptiveTextToken] = []
    var indexCounter = 0
    let punctuation = CharacterSet(charactersIn: ",.!?;:)]}\"'")

    // Split on Unicode whitespace; preserve internal punctuation as part of
    // each token so words don't fragment ("hello," stays one token).
    let pieces = text.components(separatedBy: .whitespacesAndNewlines)
    for piece in pieces where !piece.isEmpty {
      // Check whether this piece is _only_ punctuation that should attach
      // to the prior word (rare with whitespace splitting, but possible if
      // someone wrote "hello ,world").
      let attach = piece.unicodeScalars.allSatisfy { punctuation.contains($0) }
      tokens.append(
        AdaptiveTextToken(
          id: "w\(indexCounter):\(piece)",
          text: piece,
          attachToPrevious: attach && !tokens.isEmpty
        )
      )
      indexCounter += 1
    }
    return tokens
  }

  private static func tokenizeGraphemes(_ text: String) -> [AdaptiveTextToken] {
    var tokens: [AdaptiveTextToken] = []
    var i = 0
    for cluster in text {
      let s = String(cluster)
      // Skip pure whitespace graphemes — they collapse into the inter-token
      // gap so the layout stays clean.
      if s.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
        i += 1
        continue
      }
      tokens.append(
        AdaptiveTextToken(id: "g\(i):\(s)", text: s, attachToPrevious: false)
      )
      i += 1
    }
    return tokens
  }
}

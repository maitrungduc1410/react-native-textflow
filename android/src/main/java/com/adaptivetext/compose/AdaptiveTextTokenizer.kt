package com.adaptivetext.compose

import java.text.BreakIterator
import java.util.Locale

/**
 * A single token in the rendered flow. Stable [id] strings let Compose
 * smart-skip and animate position changes — words that survive a re-layout
 * keep their identity and `Modifier.animateBounds` interpolates them.
 */
data class AdaptiveTextToken(
  val id: String,
  val text: String,
  val attachToPrevious: Boolean,
)

object AdaptiveTextTokenizer {
  private val PUNCTUATION = setOf(',', '.', '!', '?', ';', ':', ')', ']', '}', '\'', '"')

  fun tokenize(text: String, mode: SplitMode): List<AdaptiveTextToken> = when (mode) {
    SplitMode.WORD -> tokenizeWords(text)
    SplitMode.GRAPHEME -> tokenizeGraphemes(text)
  }

  private fun tokenizeWords(text: String): List<AdaptiveTextToken> {
    if (text.isEmpty()) return emptyList()
    val pieces = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return pieces.mapIndexed { index, piece ->
      val attach = piece.all { it in PUNCTUATION }
      AdaptiveTextToken(
        id = "w$index:$piece",
        text = piece,
        attachToPrevious = attach && index > 0,
      )
    }
  }

  private fun tokenizeGraphemes(text: String): List<AdaptiveTextToken> {
    val out = mutableListOf<AdaptiveTextToken>()
    val iter = BreakIterator.getCharacterInstance(Locale.getDefault())
    iter.setText(text)
    var start = iter.first()
    var idx = 0
    var end = iter.next()
    while (end != BreakIterator.DONE) {
      val cluster = text.substring(start, end)
      if (cluster.isNotBlank()) {
        out.add(AdaptiveTextToken(id = "g$idx:$cluster", text = cluster, attachToPrevious = false))
      }
      start = end
      end = iter.next()
      idx++
    }
    return out
  }
}

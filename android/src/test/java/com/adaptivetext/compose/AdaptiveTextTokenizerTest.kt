package com.adaptivetext.compose

import com.google.common.truth.Truth.assertThat
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Parametrised tokenizer test. Reads the cross-platform fixtures at
 * `__fixtures__/tokenizer.json` (wired into `test/resources` from
 * `android/build.gradle`'s `sourceSets.test.resources.srcDirs`) and
 * asserts that [AdaptiveTextTokenizer] produces the exact list of
 * tokens — same `id`, same `text`, same `attachToPrevious` — for every
 * fixture entry.
 *
 * Why this test exists (AGENTS.md §4.4):
 *
 *   `AdaptiveTextTokenizer.kt` and `AdaptiveTextTokenizer.swift` MUST
 *   yield the same number of tokens and the same `attachMask` for the
 *   same input string. If they diverge, Yoga measurement and the
 *   renderer disagree on token count, which manifests as either a
 *   blank trailing line or clipped tokens after a layout pass.
 *
 *   The same JSON fixture file is consumed by the iOS XCTest target
 *   (see `example/ios/AdaptiveTextExampleTests/AdaptiveTextTokenizerTests.swift`).
 *   Whenever a parity bug crosses platforms, exactly one of the two
 *   suites turns red and tells you which side drifted.
 *
 * Adding new cases:
 *   1. Append to `__fixtures__/tokenizer.json`.
 *   2. Run `./gradlew :react-native-textflow:test` (this file).
 *   3. Run `xcodebuild test … -scheme AdaptiveTextExampleTests`.
 *   Both must stay green.
 */
@RunWith(Parameterized::class)
class AdaptiveTextTokenizerTest(private val fixture: TokenizerFixture) {

  @Test
  fun matchesTokenizerOutput() {
    val mode = when (fixture.splitBy) {
      "word" -> SplitMode.WORD
      "grapheme" -> SplitMode.GRAPHEME
      else -> error("Unknown splitBy '${fixture.splitBy}' in fixture '${fixture.name}'")
    }
    val actual = AdaptiveTextTokenizer.tokenize(fixture.input, mode)
    assertThat(actual).hasSize(fixture.expected.size)
    actual.forEachIndexed { i, token ->
      val expected = fixture.expected[i]
      assertThat(token.id).isEqualTo(expected.id)
      assertThat(token.text).isEqualTo(expected.text)
      assertThat(token.attachToPrevious).isEqualTo(expected.attachToPrevious)
    }
  }

  data class ExpectedToken(
    val id: String,
    val text: String,
    val attachToPrevious: Boolean,
  )

  data class TokenizerFixture(
    val name: String,
    val input: String,
    val splitBy: String,
    val expected: List<ExpectedToken>,
  ) {
    override fun toString(): String = name
  }

  companion object {
    @JvmStatic
    @Parameterized.Parameters(name = "{0}")
    fun fixtures(): List<TokenizerFixture> {
      // The fixture file is wired into the JVM test classpath by
      // `android/build.gradle` (sourceSets.test.resources.srcDirs).
      val stream = this::class.java.classLoader
        ?.getResourceAsStream("tokenizer.json")
        ?: error(
          "tokenizer.json not on the test classpath. Verify " +
            "android/build.gradle's `sourceSets.test.resources.srcDirs` " +
            "still points at \$rootProject/../__fixtures__."
        )
      val raw = stream.bufferedReader().use { it.readText() }
      val arr = JSONArray(raw)
      val out = ArrayList<TokenizerFixture>(arr.length())
      for (i in 0 until arr.length()) {
        val obj = arr.getJSONObject(i)
        out.add(
          TokenizerFixture(
            name = obj.getString("name"),
            input = obj.getString("input"),
            splitBy = obj.getString("splitBy"),
            expected = parseExpected(obj.getJSONArray("expected")),
          )
        )
      }
      return out
    }

    private fun parseExpected(arr: JSONArray): List<ExpectedToken> {
      val out = ArrayList<ExpectedToken>(arr.length())
      for (i in 0 until arr.length()) {
        val obj: JSONObject = arr.getJSONObject(i)
        out.add(
          ExpectedToken(
            id = obj.getString("id"),
            text = obj.getString("text"),
            attachToPrevious = obj.getBoolean("attachToPrevious"),
          )
        )
      }
      return out
    }
  }
}

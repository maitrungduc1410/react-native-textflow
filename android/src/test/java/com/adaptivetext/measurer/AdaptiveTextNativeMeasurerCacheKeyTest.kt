package com.adaptivetext.measurer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Verifies that every prop affecting per-token glyph advance contributes
 * a distinct entry in [AdaptiveTextNativeMeasurer.fontCacheKey]'s output.
 *
 * Why this test exists (AGENTS.md §8.A):
 *
 *   "If a new prop affects per-token width, also include it in the
 *   cache key inside `AdaptiveTextNativeMeasurer.kt` … Otherwise stale
 *   cached widths produce wrap mis-prediction."
 *
 * The hot path during a resize drag is:
 *
 *   1. Yoga calls our measurer once per touch frame with a new maxWidth.
 *   2. The (text, font config) is identical between frames, so the
 *      per-token width cache should hit.
 *   3. If the cache key omits a field — say `letterSpacing` — and a
 *      developer later toggles it from JS, we'll happily return the
 *      *old* widths and wrap to the *old* line count, while the
 *      Compose renderer paints with the *new* widths. The bottom line
 *      gets clipped.
 *
 * To keep this catch-all guard cheap, the test calls `fontCacheKey`
 * directly (visibility relaxed to `internal` for that purpose; see the
 * comment above the function in the source file).
 */
class AdaptiveTextNativeMeasurerCacheKeyTest {

  // A baseline set of plausible values. Each individual prop is
  // perturbed below; everything else is held constant.
  private val baseline = Args(
    fontFamily = "system",
    fontSizePx = 17f,
    fontWeight = "400",
    fontStyle = 0,
    letterSpacingPx = 0f,
    density = 3f,
    fontScale = 1f,
  )

  @Test fun differentFontFamilyProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(fontFamily = "Inter"))

  @Test fun differentFontSizeProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(fontSizePx = 18f))

  @Test fun differentFontWeightProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(fontWeight = "700"))

  @Test fun italicVsRegularProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(fontStyle = 1))

  @Test fun differentLetterSpacingProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(letterSpacingPx = 0.5f))

  @Test fun differentDensityProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(density = 2f))

  @Test fun differentFontScaleProducesDifferentKey() =
    assertDistinct(baseline, baseline.copy(fontScale = 1.3f))

  @Test fun identicalArgsProduceIdenticalKey() {
    val a = AdaptiveTextNativeMeasurer.fontCacheKey(
      baseline.fontFamily, baseline.fontSizePx, baseline.fontWeight,
      baseline.fontStyle, baseline.letterSpacingPx, baseline.density,
      baseline.fontScale,
    )
    val b = AdaptiveTextNativeMeasurer.fontCacheKey(
      baseline.fontFamily, baseline.fontSizePx, baseline.fontWeight,
      baseline.fontStyle, baseline.letterSpacingPx, baseline.density,
      baseline.fontScale,
    )
    assertThat(a).isEqualTo(b)
  }

  private fun key(args: Args): String = AdaptiveTextNativeMeasurer.fontCacheKey(
    args.fontFamily, args.fontSizePx, args.fontWeight, args.fontStyle,
    args.letterSpacingPx, args.density, args.fontScale,
  )

  private fun assertDistinct(left: Args, right: Args) {
    assertThat(key(left)).isNotEqualTo(key(right))
  }

  private data class Args(
    val fontFamily: String,
    val fontSizePx: Float,
    val fontWeight: String,
    val fontStyle: Int,
    val letterSpacingPx: Float,
    val density: Float,
    val fontScale: Float,
  )
}

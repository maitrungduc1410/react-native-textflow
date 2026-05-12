package com.adaptivetext.compose

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Snapshot of every prop pushed by JS for one render. Wrapping the entire
 * style + animation config in a single value lets Compose's smart skipping
 * compare a single `equals` instead of a chain of individual setters.
 */
data class AdaptiveTextRenderProps(
  val text: String = "",
  val splitMode: SplitMode = SplitMode.WORD,
  val fontSize: TextUnit = 17.sp,
  val fontFamily: String? = null,
  val fontWeight: FontWeight = FontWeight.Normal,
  val fontStyle: FontStyle = FontStyle.Normal,
  val textColor: Color = Color.Unspecified,
  val letterSpacing: TextUnit = TextUnit.Unspecified,
  val lineHeight: TextUnit = TextUnit.Unspecified,
  val textAlign: AdaptiveTextAlign = AdaptiveTextAlign.START,
  val wordSpacing: Float = 6f,
  val lineSpacing: Float = 4f,
  val animation: AdaptiveAnimation = AdaptiveAnimation.defaultSpring,
)

enum class SplitMode { WORD, GRAPHEME }

enum class AdaptiveTextAlign {
  AUTO, LEFT, RIGHT, CENTER, START, END;

  fun toCompose(layoutDirection: androidx.compose.ui.unit.LayoutDirection): TextAlign = when (this) {
    LEFT -> TextAlign.Left
    RIGHT -> TextAlign.Right
    CENTER -> TextAlign.Center
    START, AUTO -> TextAlign.Start
    END -> TextAlign.End
  }

  /**
   * Maps to `AdaptiveFlowLayout`'s alignment enum, resolving `LEFT`/`RIGHT`
   * against the active layout direction so the flow's per-line offset
   * pipeline can stay direction-agnostic. `AUTO` follows `start`, matching
   * RN's documented behavior and the iOS path.
   */
  fun toFlowAlignment(
    layoutDirection: androidx.compose.ui.unit.LayoutDirection,
  ): AdaptiveFlowAlignment {
    val isRtl =
      layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl
    return when (this) {
      START, AUTO -> AdaptiveFlowAlignment.START
      END -> AdaptiveFlowAlignment.END
      CENTER -> AdaptiveFlowAlignment.CENTER
      LEFT -> if (isRtl) AdaptiveFlowAlignment.END else AdaptiveFlowAlignment.START
      RIGHT -> if (isRtl) AdaptiveFlowAlignment.START else AdaptiveFlowAlignment.END
    }
  }
}

sealed class AdaptiveAnimation {
  data class Spring(val damping: Float, val stiffness: Float, val mass: Float) : AdaptiveAnimation()
  data class Timing(val durationMs: Float, val easing: AdaptiveEasing) : AdaptiveAnimation()
  data object None : AdaptiveAnimation()

  companion object {
    val defaultSpring = Spring(damping = 18f, stiffness = 220f, mass = 1f)
  }

  /**
   * Compose `FiniteAnimationSpec` for `IntOffset` (used by our placement
   * animation in `AdaptiveTextFlow`). We animate *placement only* — never
   * size — because animating size causes siblings to overlap mid-animation
   * and steal each other's trailing glyphs.
   */
  fun toOffsetSpec(): FiniteAnimationSpec<IntOffset>? = when (this) {
    is Spring -> spring(
      dampingRatio = if (damping > 0) (damping / 18f).coerceIn(0.1f, 1f) else 0.85f,
      stiffness = if (stiffness > 0) stiffness else 220f,
    )
    is Timing -> tween(
      durationMillis = (if (durationMs > 0) durationMs else 250f).toInt(),
      easing = when (easing) {
        AdaptiveEasing.LINEAR -> LinearEasing
        AdaptiveEasing.EASE_IN -> FastOutLinearInEasing
        AdaptiveEasing.EASE_OUT -> LinearOutSlowInEasing
        AdaptiveEasing.EASE_IN_OUT -> FastOutSlowInEasing
      },
    )
    None -> null
  }
}

enum class AdaptiveEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT }

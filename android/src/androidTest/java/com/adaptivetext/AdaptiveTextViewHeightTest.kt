package com.adaptivetext

import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adaptivetext.compose.AdaptiveTextRenderProps
import com.adaptivetext.compose.SplitMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumented regression guard for AGENTS.md §4.2.
 *
 * The bug:
 *
 *   When `<AdaptiveText>`'s `ComposeView` was created with
 *   `WRAP_CONTENT × WRAP_CONTENT` `LayoutParams`, FrameLayout would
 *   re-measure the ComposeView with `AT_MOST(adaptiveTextViewHeight)`
 *   and ComposeView would return *the current Compose tree's*
 *   intrinsic size — not the outer view's authoritative Yoga-given
 *   size. On a font-preset toggle, Fabric's mount sequence runs in this
 *   order:
 *
 *     1. Yoga measureContent → returns the new (e.g. comfortable) height.
 *     2. setProps → flips a mutableStateOf. Recomposition is *scheduled*.
 *     3. setLayout → AdaptiveTextView.measure(EXACTLY w, EXACTLY h_new)
 *        → super.onMeasure → ComposeView.onMeasure with AT_MOST h_new.
 *     4. ComposeView measures the *still-stale* (compact-state) Compose
 *        tree, returns the smaller intrinsic height. The outer Android
 *        measured size of ComposeView freezes at the smaller value.
 *     5. Recomposition lands a frame later → AndroidComposeView calls
 *        requestLayout(), but Fabric's ReactViewGroup ancestor swallows
 *        the request (Fabric authoritatively owns layout from the
 *        shadow tree). The outer Android size never catches up.
 *     6. The bottom of the comfortable content is clipped.
 *
 * The fix is `LayoutParams(MATCH_PARENT, MATCH_PARENT)` on the inner
 * ComposeView so its outer Android size is always the parent's
 * (FrameLayout-resolved) size — independent of what the Compose tree
 * currently thinks its intrinsic size is.
 *
 * What this test pins:
 *
 *   For an AdaptiveTextView measured with `EXACTLY w × EXACTLY h`,
 *   the inner ComposeView's `measuredHeight` must equal `h` — even
 *   across a sequence of prop pushes that *would* have changed the
 *   intrinsic Compose height. We exercise the comfortable → compact
 *   → comfortable cycle from the bug repro, with the third call
 *   being the "regression line" that previously failed.
 *
 * The actual `comfortable`/`compact` heights here are arbitrary
 * (the test does not depend on real font metrics, just on the
 * EXACTLY measure spec); using two visibly distinct values is enough
 * to demonstrate that ComposeView's outer size tracks the spec
 * regardless of whether Compose has caught up to the prop change yet.
 */
@RunWith(AndroidJUnit4::class)
class AdaptiveTextViewHeightTest {

  @Test
  fun composeViewHeightTracksYogaHeightAcrossPropChange() {
    ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
      val w = 886
      val hComfortable = 497
      val hCompact = 332

      val comfortable = AdaptiveTextRenderProps(
        text = "comfortable preset string",
        splitMode = SplitMode.WORD,
        fontSize = 22.sp,
      )
      val compact = AdaptiveTextRenderProps(
        text = "compact preset string",
        splitMode = SplitMode.WORD,
        fontSize = 14.sp,
      )

      val attached = CountDownLatch(1)
      lateinit var view: AdaptiveTextView

      scenario.onActivity { activity ->
        view = AdaptiveTextView(activity)
        val parent = FrameLayout(activity)
        parent.addView(
          view,
          FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
          )
        )
        activity.setContentView(parent)
        // Wait for ComposeView's onAttachedToWindow + the
        // remeasureOnAttach runnable AdaptiveTextView posts during
        // attach to drain (see the long-form note in
        // AdaptiveTextView.onAttachedToWindow).
        view.post { attached.countDown() }
      }
      assertThat(attached.await(5, TimeUnit.SECONDS)).isTrue()

      // Iteration 1: comfortable
      pushPropsAndMeasure(view, comfortable, w, hComfortable)
      assertThat(view.composeView.measuredHeight).isEqualTo(hComfortable)

      // Iteration 2: compact
      pushPropsAndMeasure(view, compact, w, hCompact)
      assertThat(view.composeView.measuredHeight).isEqualTo(hCompact)

      // Iteration 3: comfortable again — this is the regression line
      // from the original §4.2 bug. With the old `WRAP_CONTENT`
      // ComposeView LayoutParams, this assertion previously read
      // `hCompact` (332) because the ComposeView's outer Android
      // measure cached the smaller intrinsic value from iteration 2
      // and never caught up after Yoga grew us back to comfortable.
      pushPropsAndMeasure(view, comfortable, w, hComfortable)
      assertThat(view.composeView.measuredHeight).isEqualTo(hComfortable)
    }
  }

  /**
   * Mirrors the Fabric mount sequence for a single prop push:
   *   * push the new render-props snapshot (Compose state mutation),
   *   * call `view.measure(EXACTLY w, EXACTLY h)` (Fabric's setLayout),
   *   * `view.layout(...)` (Fabric's setLayout final step).
   * All on the UI thread, since AdaptiveTextView's onAttachedToWindow
   * posts a remeasure runnable that touches view state from the main
   * looper.
   */
  private fun pushPropsAndMeasure(
    view: AdaptiveTextView,
    props: AdaptiveTextRenderProps,
    w: Int,
    h: Int,
  ) {
    runOnMainSync {
      view.applyRenderProps(props)
      view.measure(
        MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
        MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY),
      )
      view.layout(0, 0, w, h)
    }
  }

  private fun runOnMainSync(block: () -> Unit) {
    InstrumentationRegistry.getInstrumentation().runOnMainSync { block() }
  }
}

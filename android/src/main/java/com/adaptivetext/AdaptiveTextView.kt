package com.adaptivetext

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.adaptivetext.compose.AdaptiveTextFlow
import com.adaptivetext.compose.AdaptiveTextRenderProps

/**
 * Fabric host view for `<AdaptiveText>`.
 *
 * Owns a single [ComposeView] which renders the [AdaptiveTextFlow] composable.
 * Props pushed in via [applyRenderProps] mutate Compose state directly, so
 * the UI re-composes — and `Modifier.animateBounds` interpolates each token
 * to its new line position — with zero JS-side animation work.
 */
class AdaptiveTextView : FrameLayout {

  constructor(context: Context) : super(context)
  constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
  constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
    super(context, attrs, defStyleAttr)

  // Backing state for the composable. Each prop change pushes a new
  // immutable `AdaptiveTextRenderProps` value, which lets Compose's
  // smart skipping see "props changed" with a single comparison.
  private var renderProps by mutableStateOf(AdaptiveTextRenderProps())

  // The ComposeView is added as a child eagerly during construction. Our
  // `onMeasure` short-circuits while `!isAttachedToWindow` so the inner
  // `ComposeView.onMeasure` never runs off-window — that path would call
  // `findViewTreeCompositionContext` → look for a window recomposer →
  // crash with "Cannot locate windowRecomposer" when `react-native-screens`
  // pre-mounts our subtree on a temporary off-window container during a
  // screen transition.
  //
  // Height = MATCH_PARENT (not WRAP_CONTENT) is intentional, and is what
  // keeps the renderer in lockstep with our Yoga measurer across a prop
  // toggle. With WRAP_CONTENT, `FrameLayout` measures this child with
  // `AT_MOST(adaptiveTextViewHeight)`, and `ComposeView.onMeasure` returns
  // *the current Compose tree's intrinsic size*. On a font-size toggle,
  // Fabric runs in this exact order:
  //
  //     1. Yoga `measureContent` → returns the new (e.g. comfortable)
  //        height (say 497 px).
  //     2. Fabric mount: `setProps` → `applyRenderProps(...)` flips a
  //        `mutableStateOf`. Recomposition is *scheduled* but has not
  //        yet run.
  //     3. Fabric mount: `setLayout` → AdaptiveTextView.measure(EXACTLY
  //        886, EXACTLY 497) → super.onMeasure → ComposeView.onMeasure
  //        with AT_MOST 497.
  //     4. ComposeView measures the *still-stale* Compose tree (compact,
  //        intrinsic ≈ 332 px). Its outer Android measured height becomes
  //        332.
  //     5. Next frame: recomposition runs, AdaptiveFlowLayout's intrinsic
  //        size grows to 497, AndroidComposeView calls `requestLayout()`.
  //        That walk-up is dropped at the Fabric ReactViewGroup boundary
  //        (Fabric authoritatively owns layout from the shadow tree), so
  //        ComposeView's outer Android measured size *stays at 332* even
  //        though its inner Compose content is now 497 tall.
  //     6. The bottom 165 px of the Compose content render outside
  //        ComposeView's bounds and are clipped. The user sees the last
  //        line(s) of comfortable text disappear after the toggle.
  //
  // Using MATCH_PARENT pins ComposeView's outer Android size to whatever
  // Yoga gave AdaptiveTextView (an EXACTLY spec), independent of what
  // Compose's tree currently thinks its own intrinsic size is. The
  // momentarily-stale Compose tree just renders smaller content inside a
  // correctly-sized View; recomposition then fills it. No clipping.
  // Visibility relaxed from `private` so the instrumented test
  // `AdaptiveTextViewHeightTest` (in `androidTest/`) can read
  // `composeView.measuredHeight` directly to pin the §4.2 invariant
  // (the outer Android measured size of the ComposeView must track
  // whatever Yoga gives the AdaptiveTextView, which only holds when
  // its LayoutParams are MATCH_PARENT × MATCH_PARENT). Test code
  // lives in the same Gradle module so `internal` is the smallest
  // visibility that works.
  @get:VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  internal val composeView: ComposeView = ComposeView(context).apply {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindowOrReleasedFromPool)
    setContent {
      AdaptiveTextFlow(props = renderProps)
    }
  }

  init {
    addView(composeView)
  }

  /** Pushes a new prop snapshot. Compose handles the diff. */
  fun applyRenderProps(next: AdaptiveTextRenderProps) {
    if (next != renderProps) {
      renderProps = next
    }
  }

  /** Read-modify-write helper used by the per-prop setters in the manager. */
  fun mutate(block: (AdaptiveTextRenderProps) -> AdaptiveTextRenderProps) {
    applyRenderProps(block(renderProps))
  }

  /** Snapshot accessor for read-only consumers (used by setters). */
  fun currentProps(): AdaptiveTextRenderProps = renderProps

  override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
    if (!isAttachedToWindow) {
      // See the long-form note on `composeView` above. Off-window measure
      // attempts crash inside `ComposeView`. Yoga already handed us exact
      // dimensions via our custom shadow node, so honor the spec verbatim
      // and skip child measurement. Once we attach to a real window we
      // re-run measure ourselves from `onAttachedToWindow` so ComposeView
      // gets sized and content paints — see the comment there.
      setMeasuredDimension(
        resolveSize(0, widthMeasureSpec),
        resolveSize(0, heightMeasureSpec)
      )
      return
    }
    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
  }

  override fun onAttachedToWindow() {
    super.onAttachedToWindow()
    // Force a fresh measure + layout pass on ourselves now that we (and,
    // by the time the runnable runs, our ComposeView child) are attached
    // to the window.
    //
    // Why this is needed:
    //
    //   * Fabric dispatches `setLayout(measure → layout)` on this view
    //     via the Choreographer **before** the surface attaches to the
    //     real window when `react-native-screens` pre-mounts the next
    //     screen's view tree. Our `onMeasure` short-circuits in that
    //     pre-attach window to avoid the "Cannot locate windowRecomposer"
    //     crash inside ComposeView, so super.onMeasure never runs and
    //     ComposeView is left at 0×0.
    //
    //   * After attach, Fabric does **not** dispatch another `setLayout`
    //     unless JS pushes a new layout (e.g. width changes). The
    //     standard Android `requestLayout()` walk-up that ComposeView's
    //     own attach flow performs is effectively a no-op inside Fabric:
    //     `ReactViewGroup.requestLayout()` is overridden to drop the
    //     request because Fabric authoritatively owns layout from the
    //     shadow tree side. So a child-driven re-measure never lands.
    //
    //   * The result, without this hook, is invisible text on first
    //     mount until the user changes the layout from JS — exactly the
    //     "blank container until I drag the handle" symptom we're
    //     fixing.
    //
    // We run via `post(...)` so the runnable executes after this whole
    // attach dispatch chain completes, including ComposeView's own
    // `onAttachedToWindow` (which runs `ensureCompositionCreated`). By
    // then ComposeView can resolve its CompositionContext from the
    // window recomposer, so `super.onMeasure` here forwards to
    // `FrameLayout.onMeasure → ComposeView.measure` safely.
    //
    // The runnable drives `measure()` + `layout()` directly using the
    // dimensions Fabric already applied to us via its pre-attach
    // setLayout. `requestLayout()` first sets `PFLAG_FORCE_LAYOUT` and
    // clears the measure cache on us so the immediate `measure(...)`
    // call actually re-runs `onMeasure` instead of hitting the cached
    // (zero-child) result from the pre-attach pass — even though the
    // walk-up to Fabric ancestors is dropped, the local flag set on us
    // is enough to force the re-measure here.
    removeCallbacks(remeasureOnAttach)
    post(remeasureOnAttach)
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    removeCallbacks(remeasureOnAttach)
  }

  private val remeasureOnAttach = Runnable {
    if (!isAttachedToWindow) return@Runnable
    val w = width
    val h = height
    if (w <= 0 || h <= 0) return@Runnable
    requestLayout()
    measure(
      MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
      MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
    )
    layout(left, top, right, bottom)
  }
}

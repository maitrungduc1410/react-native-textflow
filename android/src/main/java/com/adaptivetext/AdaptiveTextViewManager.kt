package com.adaptivetext

import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.adaptivetext.compose.AdaptiveAnimation
import com.adaptivetext.compose.AdaptiveEasing
import com.adaptivetext.compose.AdaptiveTextAlign
import com.adaptivetext.compose.SplitMode
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.uimanager.annotations.ReactProp
import com.facebook.react.viewmanagers.AdaptiveTextViewManagerDelegate
import com.facebook.react.viewmanagers.AdaptiveTextViewManagerInterface

/**
 * Fabric `ViewManager` for `<AdaptiveText>` on Android.
 *
 * Each codegen-driven setter mutates one field of [AdaptiveTextView]'s
 * Compose state. All animation work happens on the Compose side via
 * `Modifier.animateBounds` inside a `LookaheadScope`.
 */
@ReactModule(name = AdaptiveTextViewManager.NAME)
class AdaptiveTextViewManager : SimpleViewManager<AdaptiveTextView>(),
  AdaptiveTextViewManagerInterface<AdaptiveTextView> {

  private val mDelegate: ViewManagerDelegate<AdaptiveTextView> =
    AdaptiveTextViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<AdaptiveTextView> = mDelegate

  override fun getName(): String = NAME

  public override fun createViewInstance(context: ThemedReactContext): AdaptiveTextView =
    AdaptiveTextView(context)

  @ReactProp(name = "text")
  override fun setText(view: AdaptiveTextView?, value: String?) {
    view?.mutate { it.copy(text = value ?: "") }
  }

  @ReactProp(name = "splitBy")
  override fun setSplitBy(view: AdaptiveTextView?, value: String?) {
    val mode = if (value == "grapheme") SplitMode.GRAPHEME else SplitMode.WORD
    view?.mutate { it.copy(splitMode = mode) }
  }

  @ReactProp(name = "fontSize")
  override fun setFontSize(view: AdaptiveTextView?, value: Float) {
    val size: TextUnit = (if (value > 0) value else 17f).sp
    view?.mutate { it.copy(fontSize = size) }
  }

  @ReactProp(name = "fontFamily")
  override fun setFontFamily(view: AdaptiveTextView?, value: String?) {
    view?.mutate { it.copy(fontFamily = value?.takeIf { it.isNotEmpty() }) }
  }

  @ReactProp(name = "fontWeight")
  override fun setFontWeight(view: AdaptiveTextView?, value: String?) {
    view?.mutate { it.copy(fontWeight = parseFontWeight(value)) }
  }

  @ReactProp(name = "fontStyle")
  override fun setFontStyle(view: AdaptiveTextView?, value: String?) {
    val style = if (value == "italic") FontStyle.Italic else FontStyle.Normal
    view?.mutate { it.copy(fontStyle = style) }
  }

  @ReactProp(name = "textColor", customType = "Color")
  override fun setTextColor(view: AdaptiveTextView?, value: Int?) {
    val color = if (value == null) ComposeColor.Unspecified else ComposeColor(value)
    view?.mutate { it.copy(textColor = color) }
  }

  @ReactProp(name = "letterSpacing")
  override fun setLetterSpacing(view: AdaptiveTextView?, value: Float) {
    val ls = if (value == 0f) TextUnit.Unspecified else value.sp
    view?.mutate { it.copy(letterSpacing = ls) }
  }

  @ReactProp(name = "lineHeight")
  override fun setLineHeight(view: AdaptiveTextView?, value: Float) {
    val lh = if (value <= 0f) TextUnit.Unspecified else value.sp
    view?.mutate { it.copy(lineHeight = lh) }
  }

  @ReactProp(name = "textAlign")
  override fun setTextAlign(view: AdaptiveTextView?, value: String?) {
    val align = when (value) {
      "left" -> AdaptiveTextAlign.LEFT
      "right" -> AdaptiveTextAlign.RIGHT
      "center" -> AdaptiveTextAlign.CENTER
      "end" -> AdaptiveTextAlign.END
      "auto" -> AdaptiveTextAlign.AUTO
      else -> AdaptiveTextAlign.START
    }
    view?.mutate { it.copy(textAlign = align) }
  }

  @ReactProp(name = "wordSpacing")
  override fun setWordSpacing(view: AdaptiveTextView?, value: Float) {
    view?.mutate { it.copy(wordSpacing = value) }
  }

  @ReactProp(name = "lineSpacing")
  override fun setLineSpacing(view: AdaptiveTextView?, value: Float) {
    view?.mutate { it.copy(lineSpacing = value) }
  }

  @ReactProp(name = "animation")
  override fun setAnimation(view: AdaptiveTextView?, value: ReadableMap?) {
    view?.mutate { it.copy(animation = parseAnimation(value)) }
  }

  // MARK: - parsing helpers

  private fun parseFontWeight(raw: String?): FontWeight = when (raw) {
    "100", "ultraLight" -> FontWeight.W100
    "200", "thin" -> FontWeight.W200
    "300", "light" -> FontWeight.W300
    "400", "regular", "normal", null, "" -> FontWeight.Normal
    "500", "medium" -> FontWeight.W500
    "600", "semibold" -> FontWeight.SemiBold
    "700", "bold" -> FontWeight.Bold
    "800", "heavy" -> FontWeight.W800
    "900", "black" -> FontWeight.Black
    else -> FontWeight.Normal
  }

  private fun parseAnimation(map: ReadableMap?): AdaptiveAnimation {
    if (map == null) return AdaptiveAnimation.defaultSpring
    val type = if (map.hasKey("type") && !map.isNull("type")) map.getString("type") else "spring"
    return when (type) {
      "none" -> AdaptiveAnimation.None
      "timing" -> {
        val duration = map.takeIfHas("duration")?.toFloat() ?: 250f
        val easingStr = if (map.hasKey("easing") && !map.isNull("easing")) {
          map.getString("easing")
        } else {
          "easeInOut"
        }
        val easing = when (easingStr) {
          "linear" -> AdaptiveEasing.LINEAR
          "easeIn" -> AdaptiveEasing.EASE_IN
          "easeOut" -> AdaptiveEasing.EASE_OUT
          else -> AdaptiveEasing.EASE_IN_OUT
        }
        AdaptiveAnimation.Timing(durationMs = duration, easing = easing)
      }
      else -> {
        val damping = map.takeIfHas("damping")?.toFloat() ?: 18f
        val stiffness = map.takeIfHas("stiffness")?.toFloat() ?: 220f
        val mass = map.takeIfHas("mass")?.toFloat() ?: 1f
        AdaptiveAnimation.Spring(damping = damping, stiffness = stiffness, mass = mass)
      }
    }
  }

  private fun ReadableMap.takeIfHas(key: String): Double? =
    if (hasKey(key) && !isNull(key)) getDouble(key) else null

  companion object {
    const val NAME = "AdaptiveTextView"
  }
}

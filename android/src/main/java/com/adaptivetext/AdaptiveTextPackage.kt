package com.adaptivetext

import com.adaptivetext.measurer.AdaptiveTextNativeMeasurer
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

class AdaptiveTextViewPackage : BaseReactPackage() {
  override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> {
    // Seed the Compose-side measurer with an Application Context so the
    // Yoga measure callback can use Compose's own TextMeasurer for per-
    // token widths instead of `Paint.measureText`. Without this, the
    // Paint-based widths diverge by ~1 px per token from Compose's
    // Paragraph engine, which over a long paragraph is enough to wrap
    // an extra line on the render side that our Yoga node never
    // reserved space for — the user sees the bottom line(s) clipped
    // inside AdaptiveTextView's Fabric-controlled bounds.
    AdaptiveTextNativeMeasurer.initialize(reactContext.applicationContext)
    return listOf(AdaptiveTextViewManager())
  }

  override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? = null

  override fun getReactModuleInfoProvider() = ReactModuleInfoProvider { emptyMap() }
}

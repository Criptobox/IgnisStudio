package com.ignis.studio

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.webkit.JavascriptInterface

/** Vibración nativa para la interfaz (window.IgnisHaptic.tap("tap" | "long" | "ok")) */
class HapticBridge(private val view: View) {
    @JavascriptInterface
    fun tap(kind: String) {
        val fx = when (kind) {
            "long" -> HapticFeedbackConstants.LONG_PRESS
            "ok" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            else -> if (Build.VERSION.SDK_INT >= 27) HapticFeedbackConstants.KEYBOARD_PRESS else HapticFeedbackConstants.KEYBOARD_TAP
        }
        view.post { view.performHapticFeedback(fx) }
    }
}

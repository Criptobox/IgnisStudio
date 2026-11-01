package com.ignis.studio

import android.webkit.JavascriptInterface

/** Abre una página anclada en el visor nativo (window.IgnisWeb.open(url, nombre)). */
class WebBridge(private val act: MainActivity) {
    @JavascriptInterface
    fun open(url: String, name: String) = act.runOnUiThread { act.openInspo(url, name) }
}

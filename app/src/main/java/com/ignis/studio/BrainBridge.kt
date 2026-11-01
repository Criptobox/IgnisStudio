package com.ignis.studio

import android.content.Context
import android.webkit.JavascriptInterface
import java.io.File

/** Cerebro del agente: guarda la memoria del proyecto en un archivo propio de la app (window.IgnisBrain) */
class BrainBridge(ctx: Context) {
    private val file = File(ctx.filesDir, "cerebro.json")

    @JavascriptInterface
    fun load(): String? = if (file.exists()) file.readText() else null

    @JavascriptInterface
    fun save(json: String) {
        val tmp = File(file.parentFile, "cerebro.json.tmp")
        tmp.writeText(json)
        tmp.renameTo(file)
    }
}

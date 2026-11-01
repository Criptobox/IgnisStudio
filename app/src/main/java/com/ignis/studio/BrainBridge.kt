package com.ignis.studio

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.webkit.JavascriptInterface
import java.io.File

/** Cerebro del agente: guarda la memoria del proyecto en un archivo propio de la app (window.IgnisBrain) */
class BrainBridge(ctx: Context) {
    // AtomicFile escribe en un temporal y lo reemplaza de una vez: un corte a mitad no deja el cerebro corrupto
    private val file = AtomicFile(File(ctx.filesDir, "cerebro.json"))

    @JavascriptInterface
    @Synchronized
    fun load(): String? = try {
        file.readFully().toString(Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }

    @JavascriptInterface
    @Synchronized
    fun save(json: String) {
        val out = try { file.startWrite() } catch (e: Exception) { Log.w(TAG, "No se pudo abrir cerebro.json", e); return }
        try {
            out.write(json.toByteArray(Charsets.UTF_8))
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.w(TAG, "No se pudo guardar cerebro.json", e)
        }
    }

    private companion object { const val TAG = "IgnisBrain" }
}

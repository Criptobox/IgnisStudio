package com.ignis.studio

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import android.webkit.JavascriptInterface
import org.json.JSONArray
import java.io.File

/** Cerebro del agente: guarda la memoria del proyecto en un archivo propio de la app (window.IgnisBrain) */
class BrainBridge(ctx: Context, private val refs: File) {
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

    /** Borra una captura de Biblioteca ("/refs/ref_123.jpg") que ya no está en Guardados. */
    @JavascriptInterface
    fun deleteRef(path: String) {
        val name = path.substringAfterLast('/')
        if (REF.matches(name)) File(refs, name).delete()
    }

    /** Borra las capturas huérfanas: las que no aparecen en la lista que manda el HTML. */
    @JavascriptInterface
    fun cleanRefs(keepJson: String) {
        val keep = try {
            val a = JSONArray(keepJson)
            (0 until a.length()).map { a.optString(it).substringAfterLast('/') }.toSet()
        } catch (e: Exception) { return }
        refs.listFiles()?.forEach { if (REF.matches(it.name) && it.name !in keep) it.delete() }
    }

    private companion object {
        const val TAG = "IgnisBrain"
        val REF = Regex("ref_\\d+\\.jpg")
    }
}

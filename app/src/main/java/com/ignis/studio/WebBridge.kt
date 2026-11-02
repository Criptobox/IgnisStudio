package com.ignis.studio

import android.content.Intent
import android.webkit.JavascriptInterface
import androidx.core.content.FileProvider
import java.io.File

/**
 * Funciones del teléfono para el HTML (window.IgnisWeb):
 * abrir páginas en el visor, dictar por voz, compartir y guardar los diseños.
 */
class WebBridge(private val act: MainActivity) {
    @JavascriptInterface
    fun open(url: String, name: String) = act.runOnUiThread { act.openInspo(url, name) }

    /** Dictado por voz: el texto vuelve con window.onVoice(texto). */
    @JavascriptInterface
    fun voice() = act.runOnUiThread { act.startVoice() }

    /** Comparte un diseño como archivo .html (WhatsApp, Drive, correo…). */
    @JavascriptInterface
    fun share(name: String, html: String) {
        val dir = File(act.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, safe(name) + ".html").apply { writeText(html) }
        val uri = FileProvider.getUriForFile(act, "${act.packageName}.files", f)
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/html")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        act.runOnUiThread { act.startActivity(Intent.createChooser(send, "Compartir diseño")) }
    }

    /** Guarda el diseño donde el usuario elija (Descargas, Drive…). Resultado: window.onSaved(ok). */
    @JavascriptInterface
    fun saveFile(name: String, html: String) = act.runOnUiThread { act.saveHtml(safe(name) + ".html", html) }

    /** Convierte un diseño en app: acceso directo en la pantalla de inicio. Resultado: window.onPinned(estado). */
    @JavascriptInterface
    fun pinApp(id: String, name: String, html: String, color: String) = act.runOnUiThread { act.pinDesign(id, name, html, color) }

    @JavascriptInterface
    fun canPin(): Boolean = androidx.core.content.pm.ShortcutManagerCompat.isRequestPinShortcutSupported(act)

    private fun safe(n: String) = n.replace(Regex("[^A-Za-z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().take(40).ifEmpty { "diseno-ignis" }
}

package com.ignis.studio

import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Modelos locales de verdad (window.IgnisLLM): descarga e importa archivos .gguf,
 * los carga con llama.cpp y genera las respuestas del chat con el texto llegando en directo.
 * Los resultados vuelven al HTML con window.onLlm({...}).
 */
class LlmBridge(private val act: MainActivity, private val web: WebView) {

    /** Carpeta de modelos: privada de la app, se borra al desinstalar. */
    val dir: File = (act.getExternalFilesDir("models") ?: File(act.filesDir, "models")).apply { mkdirs() }

    private val dlCancel = AtomicBoolean(false)
    private val downloading = AtomicBoolean(false)
    private val generating = AtomicBoolean(false)
    private val loading = AtomicBoolean(false)
    @Volatile private var loadedFile = ""
    @Volatile private var initDone = false
    private val keepOn = java.util.concurrent.atomic.AtomicInteger(0)

    private fun emit(o: JSONObject) { web.post { web.evaluateJavascript("window.onLlm&&onLlm($o)", null) } }
    private fun ev(st: String) = JSONObject().put("st", st)

    /** Mantiene la pantalla encendida mientras descarga o piensa (si se apaga, Android puede cortar el trabajo). */
    private fun screenOn(on: Boolean) {
        val n = if (on) keepOn.incrementAndGet() else keepOn.updateAndGet { maxOf(0, it - 1) }
        act.runOnUiThread {
            if (n > 0) act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun safeName(name: String): String? =
        name.takeIf { it.endsWith(".gguf", true) && !it.contains('/') && !it.contains('\\') && !it.startsWith(".") }

    private fun freeBytes() = try { StatFs(dir.absolutePath).availableBytes } catch (e: Exception) { Long.MAX_VALUE }

    private fun threads() = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(2, 4)

    @JavascriptInterface
    fun available(): Boolean = LlamaNative.available

    /** Estado del motor para la interfaz: {ok, err, abi, v} */
    @JavascriptInterface
    fun status(): String = JSONObject()
        .put("ok", LlamaNative.available)
        .put("err", LlamaNative.loadError ?: "")
        .put("abi", android.os.Build.SUPPORTED_ABIS.joinToString())
        .put("v", BuildConfig.VERSION_NAME)
        .toString()

    /** Modelos en el teléfono: [{file, size}] y descargas a medias [{file, part}]. */
    @JavascriptInterface
    fun models(): String {
        val a = JSONArray()
        dir.listFiles()?.sortedBy { it.name }?.forEach { f ->
            when {
                f.name.endsWith(".gguf", true) -> a.put(JSONObject().put("file", f.name).put("size", f.length()))
                f.name.endsWith(".gguf.part", true) -> a.put(JSONObject().put("file", f.name.removeSuffix(".part")).put("part", f.length()))
            }
        }
        return a.toString()
    }

    @JavascriptInterface
    fun loaded(): String = if (LlamaNative.available && loadedFile.isNotEmpty()) loadedFile else ""

    @JavascriptInterface
    fun freeSpace(): Long = freeBytes()

    // ---------- Descargas (con reanudación) ----------

    /** sha256 y size (bytes) son los oficiales de Hugging Face; vacíos o 0 si no se conocen. */
    @JavascriptInterface
    fun download(id: String, url: String, file: String, sha256: String, size: Long) {
        val name = safeName(file) ?: return emit(ev("dlerr").put("id", id).put("msg", "Nombre de archivo no válido"))
        if (!url.startsWith("https://")) return emit(ev("dlerr").put("id", id).put("msg", "Dirección no válida"))
        if (!downloading.compareAndSet(false, true)) return emit(ev("dlerr").put("id", id).put("msg", "Ya hay una descarga en curso"))
        dlCancel.set(false)
        screenOn(true)
        thread(name = "ignis-dl") {
            val part = File(dir, "$name.part")
            var c: HttpURLConnection? = null
            try {
                val have = if (part.exists()) part.length() else 0L
                c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "IgnisStudio/${BuildConfig.VERSION_NAME}")
                if (have > 0) c.setRequestProperty("Range", "bytes=$have-")
                c.connectTimeout = 20_000; c.readTimeout = 60_000
                val code = c.responseCode
                if (code == 401 || code == 403) throw Exception("Este modelo pide iniciar sesión en Hugging Face. Descárgalo en el navegador y usa «Importar .gguf».")
                if (code == 404) throw Exception("El archivo ya no está en Hugging Face")
                if (code == 416 && have > 0) { finishDownload(part, name, id, sha256, size); return@thread }
                if (code != 200 && code != 206) throw Exception("El servidor respondió $code")
                val resumed = code == 206
                var got = if (resumed) have else 0L
                val total = if (c.contentLengthLong > 0) got + c.contentLengthLong else -1L
                if (total > 0 && total - got > freeBytes() - 200_000_000L)
                    throw Exception("No hay espacio: hacen falta ${"%.1f".format((total - got) / 1e9)} GB libres")
                var lastEmit = 0L
                c.inputStream.use { inp ->
                    java.io.FileOutputStream(part, resumed).use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            if (dlCancel.get()) { emit(ev("dlcancel").put("id", id)); return@thread }
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); got += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 400) {
                                lastEmit = now
                                emit(ev("dl").put("id", id).put("got", got).put("size", total)
                                    .put("p", if (total > 0) (got * 100 / total).toInt() else 0))
                            }
                        }
                    }
                }
                if (total > 0 && got != total) throw Exception("La descarga se cortó. Toca otra vez para continuar.")
                finishDownload(part, name, id, sha256, size)
            } catch (e: Exception) {
                val msg = if (e is java.io.IOException || e.message.isNullOrBlank()) "Se perdió la conexión. Toca otra vez para continuar." else e.message
                emit(ev("dlerr").put("id", id).put("msg", msg))
            } finally {
                c?.disconnect()
                downloading.set(false)
                screenOn(false)
            }
        }
    }

    private fun finishDownload(part: File, name: String, id: String, sha256: String, size: Long) {
        val dest = File(dir, name)
        if (size > 0 && part.length() != size) {
            part.delete(); throw Exception("El archivo descargado no tiene el tamaño correcto. Vuelve a descargarlo.")
        }
        // Huella oficial: garantiza que el modelo es exactamente el publicado y no está dañado
        if (sha256.length == 64) {
            emit(ev("dlcheck").put("id", id))
            val md = java.security.MessageDigest.getInstance("SHA-256")
            part.inputStream().use { inp ->
                val buf = ByteArray(1024 * 1024)
                while (true) { val n = inp.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            }
            val got = md.digest().joinToString("") { "%02x".format(it) }
            if (!got.equals(sha256, true)) { part.delete(); throw Exception("El archivo descargado está dañado. Vuelve a descargarlo.") }
        }
        if (!checkGguf(part)) { part.delete(); throw Exception("El archivo descargado no es un modelo .gguf válido") }
        if (!part.renameTo(dest)) throw Exception("No se pudo guardar el modelo")
        emit(ev("dlok").put("id", id).put("file", name).put("size", dest.length()))
    }

    /** Los archivos GGUF empiezan por los bytes "GGUF". */
    private fun checkGguf(f: File): Boolean = try {
        f.inputStream().use { val b = ByteArray(4); it.read(b) == 4 && String(b, Charsets.US_ASCII) == "GGUF" }
    } catch (e: Exception) { false }

    @JavascriptInterface
    fun cancelDownload() { dlCancel.set(true) }

    @JavascriptInterface
    fun delete(file: String): Boolean {
        val name = safeName(file) ?: return false
        if (name == loadedFile) { LlamaNative.stop(); thread { LlamaNative.unload() }; loadedFile = "" }
        File(dir, "$name.part").delete()
        return File(dir, name).delete()
    }

    // ---------- Importar un .gguf del teléfono ----------

    @JavascriptInterface
    fun importModel() = act.runOnUiThread { act.pickModel() }

    /** Lo llama MainActivity con el archivo elegido. */
    fun onPicked(uri: Uri?) {
        if (uri == null) return emit(ev("impcancel"))
        screenOn(true)
        thread(name = "ignis-import") {
            var tmp: File? = null
            try {
                var name = "modelo.gguf"; var size = -1L
                act.contentResolver.query(uri, null, null, null, null)?.use { cur ->
                    if (cur.moveToFirst()) {
                        val ni = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val si = cur.getColumnIndex(OpenableColumns.SIZE)
                        if (ni >= 0) cur.getString(ni)?.let { name = it }
                        if (si >= 0 && !cur.isNull(si)) size = cur.getLong(si)
                    }
                }
                name = name.replace(Regex("[^A-Za-z0-9._-]"), "_").trimStart('.')
                if (!name.endsWith(".gguf", true)) throw Exception("Elige un archivo .gguf")
                if (size > 0 && size > freeBytes() - 200_000_000L) throw Exception("No hay espacio para copiar el modelo")
                val t = File(dir, "$name.import").also { tmp = it }
                var got = 0L; var lastEmit = 0L
                (act.contentResolver.openInputStream(uri) ?: throw Exception("No se pudo abrir el archivo")).use { inp ->
                    t.outputStream().use { out ->
                        val buf = ByteArray(256 * 1024)
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); got += n
                            val now = System.currentTimeMillis()
                            if (now - lastEmit > 400) {
                                lastEmit = now
                                emit(ev("imp").put("got", got).put("size", size).put("p", if (size > 0) (got * 100 / size).toInt() else 0))
                            }
                        }
                    }
                }
                if (!checkGguf(t)) throw Exception("Ese archivo no es un modelo .gguf válido")
                val dest = File(dir, name)
                dest.delete()
                if (!t.renameTo(dest)) throw Exception("No se pudo guardar el modelo")
                tmp = null
                emit(ev("impok").put("file", name).put("size", dest.length()))
            } catch (e: Exception) {
                emit(ev("imperr").put("msg", e.message ?: "No se pudo importar"))
            } finally {
                tmp?.delete()
                screenOn(false)
            }
        }
    }

    // ---------- Cargar y generar ----------

    @JavascriptInterface
    fun load(file: String, nCtx: Int) {
        val name = safeName(file) ?: return emit(ev("loaderr").put("msg", "Archivo no válido"))
        if (!LlamaNative.available) return emit(ev("loaderr").put("msg", "El motor local no está disponible en este teléfono"))
        val f = File(dir, name)
        if (!f.exists()) return emit(ev("loaderr").put("msg", "El modelo ya no está en el teléfono"))
        if (generating.get()) return emit(ev("loaderr").put("msg", "Espera a que Ignis termine de responder"))
        if (!loading.compareAndSet(false, true)) return
        emit(ev("loading").put("file", name))
        thread(name = "ignis-load") {
            try {
                if (!initDone) { LlamaNative.init(act.applicationInfo.nativeLibraryDir); initDone = true }
                val t0 = System.currentTimeMillis()
                loadedFile = ""
                val err = LlamaNative.load(f.absolutePath, nCtx.coerceIn(512, 16384), threads())
                if (err.isEmpty()) {
                    loadedFile = name
                    emit(ev("loaded").put("file", name).put("desc", LlamaNative.describe()).put("ms", System.currentTimeMillis() - t0))
                } else emit(ev("loaderr").put("msg", err))
            } catch (e: Throwable) {
                emit(ev("loaderr").put("msg", "No se pudo cargar: ${e.message ?: "error desconocido"}"))
            } finally {
                loading.set(false)
            }
        }
    }

    @JavascriptInterface
    fun unload() {
        LlamaNative.stop(); loadedFile = ""
        thread { if (LlamaNative.available) LlamaNative.unload() }
    }

    /** json = {msgs:[{r:"system"|"user"|"assistant", c:"…"}], max: 1024, temp: 0.7} */
    @JavascriptInterface
    fun generate(json: String) {
        if (loadedFile.isEmpty()) return emit(ev("generr").put("msg", "No hay ningún modelo cargado"))
        if (!generating.compareAndSet(false, true)) return emit(ev("generr").put("msg", "Ignis ya está respondiendo"))
        screenOn(true)
        thread(name = "ignis-gen") {
            try {
                val j = JSONObject(json)
                val msgs = j.getJSONArray("msgs")
                val roles = Array(msgs.length()) { msgs.getJSONObject(it).optString("r", "user").toByteArray() }
                val contents = Array(msgs.length()) { msgs.getJSONObject(it).optString("c").toByteArray() }
                val sb = StringBuilder(); var pend = StringBuilder(); var last = 0L; var n = 0
                val t0 = System.currentTimeMillis()
                val err = LlamaNative.generate(roles, contents, j.optInt("max", 1024).coerceIn(16, 8192),
                    j.optDouble("temp", 0.7).toFloat(), object : LlamaNative.TokenCallback {
                        override fun onToken(bytes: ByteArray): Boolean {
                            val s = String(bytes, Charsets.UTF_8)
                            sb.append(s); pend.append(s); n++
                            val now = System.currentTimeMillis()
                            // se envía en grupos para no saturar la interfaz
                            if (now - last > 60) { last = now; emit(ev("tok").put("t", pend.toString())); pend = StringBuilder() }
                            return true
                        }
                    })
                if (pend.isNotEmpty()) emit(ev("tok").put("t", pend.toString()))
                val ms = System.currentTimeMillis() - t0
                if (err.isEmpty()) emit(ev("end").put("text", sb.toString()).put("n", n).put("ms", ms))
                else emit(ev("generr").put("msg", err).put("text", sb.toString()))
            } catch (e: Throwable) {
                emit(ev("generr").put("msg", "Error al generar: ${e.message ?: "desconocido"}"))
            } finally {
                generating.set(false)
                screenOn(false)
            }
        }
    }

    @JavascriptInterface
    fun stop() { if (LlamaNative.available) LlamaNative.stop() }

    /** Al cerrar la app se libera la memoria del modelo. */
    fun release() {
        dlCancel.set(true)
        if (LlamaNative.available) { LlamaNative.stop(); thread { LlamaNative.unload() } }
    }
}

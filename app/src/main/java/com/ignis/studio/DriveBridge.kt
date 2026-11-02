package com.ignis.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

/**
 * Google Drive (window.IgnisDrive): Ignis busca tus diseños y se los pasa al modelo.
 * Solo lectura (drive.readonly). La app se identifica ante Google por su paquete y la huella de su firma,
 * registrados como cliente OAuth «Android» en Google Cloud: no hay claves dentro de la app.
 * Resultados: window.onDrive({st:'connected'|'results'|'file'|'err', ...}).
 */
class DriveBridge(private val act: MainActivity, private val web: WebView) {

    private val prefs = act.getSharedPreferences("ignis_drive", Context.MODE_PRIVATE)
    private val scope = Scope("https://www.googleapis.com/auth/drive.readonly")
    @Volatile private var token: String? = null

    private fun emit(o: JSONObject) { web.post { web.evaluateJavascript("window.onDrive&&onDrive($o)", null) } }
    private fun ev(st: String) = JSONObject().put("st", st)

    @JavascriptInterface
    fun connected(): Boolean = prefs.getBoolean("on", false)

    /** Inicia sesión con Google y pide permiso de solo lectura (muestra la pantalla de Google la primera vez). */
    @JavascriptInterface
    fun connect() = authorize(true) { t, err ->
        if (t != null) { prefs.edit().putBoolean("on", true).apply(); emit(ev("connected")) }
        else emit(ev("err").put("msg", err ?: "No se pudo conectar"))
    }

    @JavascriptInterface
    fun disconnect() { token = null; prefs.edit().putBoolean("on", false).apply() }

    /** Pide (o renueva en silencio) el token de acceso. cb(token, error) */
    private fun authorize(interactive: Boolean, cb: (String?, String?) -> Unit) {
        act.runOnUiThread {
            val req = AuthorizationRequest.builder().setRequestedScopes(listOf(scope)).build()
            val client = Identity.getAuthorizationClient(act)
            client.authorize(req)
                .addOnSuccessListener { r ->
                    val pi = r.pendingIntent
                    if (r.hasResolution() && pi != null) {
                        if (!interactive) { cb(null, "Vuelve a conectar Google Drive"); return@addOnSuccessListener }
                        act.launchDriveAuth(pi) { data ->
                            if (data == null) return@launchDriveAuth cb(null, "Conexión cancelada")
                            try {
                                val t = client.getAuthorizationResultFromIntent(data).accessToken
                                token = t; cb(t, if (t == null) "Google no devolvió acceso" else null)
                            } catch (e: ApiException) { cb(null, friendly(e)) }
                        }
                    } else { token = r.accessToken; cb(r.accessToken, if (r.accessToken == null) "Google no devolvió acceso" else null) }
                }
                .addOnFailureListener { e -> cb(null, if (e is ApiException) friendly(e) else "No se pudo conectar con Google") }
        }
    }

    private fun friendly(e: ApiException) = when (e.statusCode) {
        10 -> "La app no está registrada en Google Cloud: revisa el cliente OAuth Android (paquete com.ignis.studio y huella SHA-1)"
        12501, 16 -> "Conexión cancelada"
        7 -> "Sin conexión a internet"
        else -> "Google respondió con el error ${e.statusCode}"
    }

    /** Token válido (renovado en silencio si hace falta) o null. Se llama desde un hilo de trabajo. */
    private fun tokenNow(): String? {
        token?.let { return it }
        val lock = Object(); var got: String? = null; var done = false
        authorize(false) { t, _ -> synchronized(lock) { got = t; done = true; lock.notifyAll() } }
        synchronized(lock) { val end = System.currentTimeMillis() + 20_000; while (!done && System.currentTimeMillis() < end) lock.wait(1000) }
        return got
    }

    private class Http(val code: Int) : Exception("HTTP $code")

    private fun get(url: String, max: Int): ByteArray {
        repeat(2) { attempt ->
            val t = tokenNow() ?: throw IllegalStateException("Vuelve a conectar Google Drive")
            val c = URL(url).openConnection() as HttpURLConnection
            try {
                c.setRequestProperty("Authorization", "Bearer $t")
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                val code = c.responseCode
                if (code == 401 && attempt == 0) { token = null; return@repeat }
                if (code !in 200..299) throw Http(code)
                c.inputStream.use { inp ->
                    val out = ByteArrayOutputStream(); val buf = ByteArray(64 * 1024)
                    while (true) { val n = inp.read(buf); if (n < 0) break; out.write(buf, 0, n); if (out.size() > max) break }
                    return out.toByteArray()
                }
            } finally { c.disconnect() }
        }
        throw IllegalStateException("Vuelve a conectar Google Drive")
    }

    private fun msg(e: Exception) = when (e) {
        is Http -> when (e.code) { 403 -> "Activa la API de Google Drive en tu proyecto de Google Cloud"; 404 -> "El archivo ya no está en Drive"; else -> "Drive respondió ${e.code}" }
        is IllegalStateException -> e.message ?: "Vuelve a conectar Google Drive"
        else -> "No se pudo conectar con Google Drive"
    }

    /** Busca diseños: words = ["café","landing"] (vacío = los más recientes). */
    @JavascriptInterface
    fun search(wordsJson: String, reqId: String) {
        thread(name = "ignis-drive-search") {
            try {
                val words = JSONArray(wordsJson).let { a -> (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }.take(5) }
                val types = "(mimeType contains 'image/' or mimeType = 'text/html' or mimeType = 'text/plain' or " +
                    "mimeType = 'application/vnd.google-apps.document' or mimeType = 'application/vnd.google-apps.presentation')"
                val esc = { w: String -> w.replace("\\", "\\\\").replace("'", "\\'") }
                val text = if (words.isEmpty()) "" else " and (" + words.joinToString(" or ") { "name contains '${esc(it)}' or fullText contains '${esc(it)}'" } + ")"
                val q = "trashed = false and $types$text"
                var url = "https://www.googleapis.com/drive/v3/files?pageSize=25&fields=" +
                    URLEncoder.encode("files(id,name,mimeType,modifiedTime,size)", "UTF-8") + "&q=" + URLEncoder.encode(q, "UTF-8")
                if (words.isEmpty()) url += "&orderBy=modifiedTime%20desc" // con búsqueda de texto Drive no permite ordenar
                val j = JSONObject(String(get(url, 2_000_000)))
                emit(ev("results").put("reqId", reqId).put("files", j.optJSONArray("files") ?: JSONArray()))
            } catch (e: Exception) { emit(ev("err").put("reqId", reqId).put("msg", msg(e))) }
        }
    }

    /** Lee un archivo: imágenes reducidas a 640 px (data URL), HTML/texto y documentos de Google como texto. */
    @JavascriptInterface
    fun fetch(id: String, mime: String, name: String, reqId: String) {
        if (!Regex("[A-Za-z0-9_-]{5,200}").matches(id)) return emit(ev("err").put("reqId", reqId).put("msg", "Archivo no válido"))
        thread(name = "ignis-drive-fetch") {
            try {
                val base = "https://www.googleapis.com/drive/v3/files/$id"
                val o = ev("file").put("reqId", reqId).put("id", id).put("name", name).put("mime", mime)
                when {
                    mime.startsWith("image/") && mime != "image/svg+xml" -> {
                        val bytes = get("$base?alt=media", 25_000_000)
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                        var s = 1; while (bounds.outWidth / (s * 2) >= 640) s *= 2
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
                            ?: throw IllegalStateException("No se pudo leer la imagen")
                        val out = ByteArrayOutputStream(); bmp.compress(Bitmap.CompressFormat.JPEG, 80, out); bmp.recycle()
                        o.put("kind", "image").put("data", "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP))
                    }
                    mime.startsWith("application/vnd.google-apps.") ->
                        o.put("kind", "text").put("data", String(get("$base/export?mimeType=text%2Fplain", 200_000)))
                    else -> o.put("kind", if (mime == "text/html" || name.endsWith(".html", true)) "html" else "text")
                        .put("data", String(get("$base?alt=media", 400_000)))
                }
                emit(o)
            } catch (e: Exception) { emit(ev("err").put("reqId", reqId).put("msg", msg(e))) }
        }
    }
}

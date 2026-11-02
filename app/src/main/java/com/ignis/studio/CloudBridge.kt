package com.ignis.studio

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlin.concurrent.thread

/**
 * Modelos en la nube con la clave de API del usuario (window.IgnisCloud).
 * Claude va por el SDK oficial (ClaudeStream.java); ChatGPT, DeepSeek, Mistral, Grok y Perplexity
 * por su API compatible con OpenAI; Gemini por su API propia. Las respuestas llegan al chat como las
 * del modelo local: window.onLlm({st:'tok'|'end'|'generr'}). Las claves se guardan cifradas.
 */
class CloudBridge(private val ctx: Context, private val web: WebView) {

    private data class P(val kind: String, val base: String)
    private val providers = mapOf(
        "chatgpt" to P("openai", "https://api.openai.com/v1"),
        "deepseek" to P("openai", "https://api.deepseek.com/v1"),
        "mistral" to P("openai", "https://api.mistral.ai/v1"),
        "grok" to P("openai", "https://api.x.ai/v1"),
        "perplexity" to P("openai", "https://api.perplexity.ai"),
        "gemini" to P("gemini", "https://generativelanguage.googleapis.com/v1beta"),
        "claude" to P("claude", ""),
    )

    private val prefs = ctx.getSharedPreferences("ignis_cloud", Context.MODE_PRIVATE)
    private val stop = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)

    private fun emit(fn: String, o: JSONObject) { web.post { web.evaluateJavascript("window.$fn&&$fn($o)", null) } }
    private fun ev(st: String) = JSONObject().put("st", st)

    // ---------- Claves cifradas con el almacén seguro de Android ----------
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("ignis_cloud_key", null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(KeyGenParameterSpec.Builder("ignis_cloud_key", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return kg.generateKey()
    }

    private fun encrypt(s: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, secretKey()) }
        val out = c.iv + c.doFinal(s.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun decrypt(s: String): String? = try {
        val all = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, all, 0, 12))
        String(c.doFinal(all, 12, all.size - 12))
    } catch (e: Exception) { null }

    private fun key(id: String): String? = prefs.getString("k_$id", null)?.let { decrypt(it) }

    @JavascriptInterface
    fun setKey(id: String, key: String): Boolean {
        if (id !in providers || key.isBlank()) return false
        return try { prefs.edit().putString("k_$id", encrypt(key.trim())).apply(); true } catch (e: Exception) { false }
    }

    @JavascriptInterface
    fun removeKey(id: String) { prefs.edit().remove("k_$id").apply() }

    /** Proveedores con clave guardada: ["claude","gemini",…] (nunca devuelve las claves). */
    @JavascriptInterface
    fun connected(): String = JSONArray(providers.keys.filter { prefs.contains("k_$it") }).toString()

    // ---------- Lista de modelos (también sirve para probar la clave) ----------
    @JavascriptInterface
    fun models(id: String) {
        val p = providers[id] ?: return
        val k = key(id) ?: return emit("onCloud", ev("err").put("id", id).put("msg", "Falta la clave de API"))
        thread(name = "ignis-cloud-models") {
            try {
                val list = JSONArray()
                when (p.kind) {
                    "openai" -> {
                        val j = JSONObject(http("${p.base}/models", "GET", null, mapOf("Authorization" to "Bearer $k")))
                        val a = j.optJSONArray("data") ?: JSONArray()
                        for (i in 0 until a.length()) a.getJSONObject(i).optString("id").takeIf { it.isNotEmpty() }?.let { list.put(it) }
                    }
                    "gemini" -> {
                        val j = JSONObject(http("${p.base}/models?pageSize=200&key=${URLEncoder.encode(k, "UTF-8")}", "GET", null, emptyMap()))
                        val a = j.optJSONArray("models") ?: JSONArray()
                        for (i in 0 until a.length()) {
                            val m = a.getJSONObject(i)
                            val methods = m.optJSONArray("supportedGenerationMethods")?.toString() ?: ""
                            if ("generateContent" in methods) list.put(m.optString("name").removePrefix("models/"))
                        }
                    }
                    "claude" -> {
                        // Modelos actuales de Claude; la clave se comprueba al primer mensaje
                        listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5").forEach { list.put(it) }
                    }
                }
                emit("onCloud", ev("models").put("id", id).put("list", list))
            } catch (e: HttpError) {
                emit("onCloud", ev("err").put("id", id).put("msg", e.friendly()))
            } catch (e: Exception) {
                emit("onCloud", ev("err").put("id", id).put("msg", "No se pudo conectar. Revisa tu conexión"))
            }
        }
    }

    // ---------- Generar ----------
    /** json = {id, model, msgs:[{r,c}], max, temp} */
    @JavascriptInterface
    fun generate(json: String) {
        if (!busy.compareAndSet(false, true)) return emit("onLlm", ev("generr").put("msg", "Ya hay una respuesta en curso"))
        stop.set(false)
        thread(name = "ignis-cloud-gen") {
            val sb = StringBuilder(); var pend = StringBuilder(); var last = 0L; var n = 0
            val t0 = System.currentTimeMillis()
            fun push(t: String): Boolean {
                sb.append(t); pend.append(t); n++
                val now = System.currentTimeMillis()
                if (now - last > 60) { last = now; emit("onLlm", ev("tok").put("t", pend.toString())); pend = StringBuilder() }
                return !stop.get()
            }
            var err = ""
            try {
                val j = JSONObject(json)
                val id = j.getString("id"); val model = j.getString("model")
                val p = providers[id] ?: throw IllegalArgumentException("Proveedor desconocido")
                val k = key(id) ?: throw IllegalStateException("Falta la clave de API de este servicio")
                val msgs = j.getJSONArray("msgs")
                val max = j.optInt("max", 4000).coerceIn(256, 64000)
                val temp = j.optDouble("temp", 0.7)
                err = when (p.kind) {
                    "claude" -> {
                        var system = ""; val roles = ArrayList<String>(); val contents = ArrayList<String>()
                        for (i in 0 until msgs.length()) {
                            val m = msgs.getJSONObject(i)
                            if (m.optString("r") == "system") system += m.optString("c") + "\n"
                            else { roles.add(m.optString("r")); contents.add(m.optString("c")) }
                        }
                        ClaudeStream.stream(k, model, system.trim(), roles, contents, maxOf(max, 16000).toLong(), stop) { push(it) }
                    }
                    "gemini" -> streamGemini(p, k, model, msgs, max, temp) { push(it) }
                    else -> streamOpenAI(p, k, model, msgs, max, temp) { push(it) }
                }
            } catch (e: HttpError) { err = e.friendly() }
            catch (e: Exception) { err = e.message?.takeIf { it.isNotBlank() } ?: "No se pudo conectar" }
            if (pend.isNotEmpty()) emit("onLlm", ev("tok").put("t", pend.toString()))
            val ms = System.currentTimeMillis() - t0
            if (err.isEmpty()) emit("onLlm", ev("end").put("text", sb.toString()).put("n", n).put("ms", ms))
            else emit("onLlm", ev("generr").put("msg", err).put("text", sb.toString()))
            busy.set(false)
        }
    }

    @JavascriptInterface
    fun stop() { stop.set(true) }

    private fun streamOpenAI(p: P, key: String, model: String, msgs: JSONArray, max: Int, temp: Double, push: (String) -> Boolean): String {
        val body = JSONObject().put("model", model).put("stream", true).put("max_tokens", max).put("temperature", temp)
            .put("messages", JSONArray().apply {
                for (i in 0 until msgs.length()) { val m = msgs.getJSONObject(i); put(JSONObject().put("role", m.optString("r")).put("content", m.optString("c"))) }
            })
        sse("${p.base}/chat/completions", body, mapOf("Authorization" to "Bearer $key")) { data ->
            val d = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
            val t = d?.optString("content", "") ?: ""
            if (t.isNotEmpty() && t != "null") push(t) else true
        }
        return ""
    }

    private fun streamGemini(p: P, key: String, model: String, msgs: JSONArray, max: Int, temp: Double, push: (String) -> Boolean): String {
        var system = ""; val contents = JSONArray()
        for (i in 0 until msgs.length()) {
            val m = msgs.getJSONObject(i)
            when (m.optString("r")) {
                "system" -> system += m.optString("c") + "\n"
                else -> contents.put(JSONObject().put("role", if (m.optString("r") == "assistant") "model" else "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", m.optString("c")))))
            }
        }
        val body = JSONObject().put("contents", contents)
            .put("generationConfig", JSONObject().put("maxOutputTokens", max).put("temperature", temp))
        if (system.isNotBlank()) body.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system.trim()))))
        sse("${p.base}/models/${URLEncoder.encode(model, "UTF-8")}:streamGenerateContent?alt=sse", body, mapOf("x-goog-api-key" to key)) { data ->
            val parts = JSONObject(data).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            var go = true
            if (parts != null) for (i in 0 until parts.length()) {
                val t = parts.getJSONObject(i).optString("text", "")
                if (t.isNotEmpty() && !parts.getJSONObject(i).optBoolean("thought", false)) go = push(t) && go
            }
            go
        }
        return ""
    }

    // ---------- HTTP ----------
    private class HttpError(val code: Int, val body: String) : Exception("HTTP $code") {
        fun friendly(): String = when (code) {
            400 -> "La petición no es válida para este modelo" + detail()
            401, 403 -> "La clave de API no es válida o no tiene permiso"
            404 -> "Ese modelo no existe o tu cuenta no tiene acceso"
            402 -> "Tu cuenta no tiene saldo"
            429 -> "Límite de uso alcanzado. Espera un momento o revisa tu plan"
            in 500..599 -> "El servicio tiene problemas ahora mismo ($code)"
            else -> "El servicio respondió $code" + detail()
        }
        private fun detail(): String = try {
            val m = JSONObject(body).let { it.optJSONObject("error")?.optString("message") ?: it.optString("message") }
            if (m.isNullOrBlank()) "" else ": " + m.take(140)
        } catch (e: Exception) { "" }
    }

    private fun open(url: String, method: String, headers: Map<String, String>): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000; readTimeout = 120_000
            setRequestProperty("User-Agent", "IgnisStudio/${BuildConfig.VERSION_NAME}")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }

    private fun http(url: String, method: String, body: JSONObject?, headers: Map<String, String>): String {
        val c = open(url, method, headers)
        try {
            if (body != null) { c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code, c.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    /** Lee un flujo SSE ("data: {...}") y pasa cada evento a onData; si devuelve false, se corta. */
    private fun sse(url: String, body: JSONObject, headers: Map<String, String>, onData: (String) -> Boolean) {
        val c = open(url, "POST", headers + ("Accept" to "text/event-stream"))
        try {
            c.doOutput = true; c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = c.responseCode
            if (code !in 200..299) throw HttpError(code, c.errorStream?.bufferedReader()?.use { it.readText() } ?: "")
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    if (stop.get()) break
                    val line = r.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    if (data.isEmpty()) continue
                    val go = try { onData(data) } catch (e: org.json.JSONException) { true }
                    if (!go) break
                }
            }
        } finally { c.disconnect() }
    }
}

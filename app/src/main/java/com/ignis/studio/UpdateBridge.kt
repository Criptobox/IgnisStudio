package com.ignis.studio

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Actualizaciones dentro de la app (window.IgnisUpdate).
 * Busca la última versión publicada en GitHub Releases, descarga la APK mostrando el progreso
 * y la instala con PackageInstaller: Android muestra su aviso encima de la app, sin salir de Ajustes.
 */
class UpdateBridge(private val act: MainActivity, private val web: WebView) {

    private var apkUrl: String? = null
    private var apkSize = 0L
    private var version = ""

    private fun emit(o: JSONObject) = web.post { web.evaluateJavascript("window.onUpd&&onUpd($o)", null) }
    private fun state(st: String) = JSONObject().put("st", st)

    @JavascriptInterface
    fun version(): String = BuildConfig.VERSION_NAME

    @JavascriptInterface
    fun check() {
        thread {
            try {
                val c = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                    .openConnection() as HttpURLConnection
                c.setRequestProperty("Accept", "application/vnd.github+json")
                c.connectTimeout = 10_000; c.readTimeout = 15_000
                if (c.responseCode == 404) { emit(state("ok")); return@thread }
                if (c.responseCode != 200) throw Exception("GitHub respondió ${c.responseCode}")
                val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val tag = j.optString("tag_name")
                val code = tag.substringAfterLast('.').filter { it.isDigit() }.toIntOrNull() ?: 0
                val assets = j.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk") }
                if (code > BuildConfig.VERSION_CODE && apk != null) {
                    apkUrl = apk.getString("browser_download_url")
                    apkSize = apk.optLong("size")
                    version = tag.removePrefix("v")
                    emit(state("new").put("v", version).put("size", apkSize).put("notes", j.optString("body")))
                } else emit(state("ok"))
            } catch (e: Exception) {
                emit(state("err").put("msg", "No se pudo conectar con GitHub. Revisa tu conexión."))
            }
        }
    }

    @JavascriptInterface
    fun download() {
        val url = apkUrl ?: return check()
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            emit(state("perm")); return
        }
        thread {
            try {
                val f = File(act.cacheDir, "update.apk")
                val c = URL(url).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 15_000; c.readTimeout = 30_000
                val total = c.contentLengthLong.takeIf { it > 0 } ?: apkSize
                var got = 0L; var last = -1
                c.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = inp.read(buf); if (n < 0) break
                            out.write(buf, 0, n); got += n
                            val p = if (total > 0) (got * 100 / total).toInt() else 0
                            if (p != last) { last = p; emit(state("dl").put("p", p).put("got", got).put("size", total).put("v", version)) }
                        }
                    }
                }
                install(f)
            } catch (e: Exception) {
                emit(state("err").put("msg", "La descarga se interrumpió. Inténtalo de nuevo."))
            }
        }
    }

    /** Abre el ajuste de Android para permitir que la app instale sus actualizaciones (solo la primera vez). */
    @JavascriptInterface
    fun allow() {
        act.runOnUiThread {
            act.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${act.packageName}")))
        }
    }

    private fun install(f: File) {
        val pi = act.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= 31) params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            f.inputStream().use { i -> s.openWrite("ignis.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val p = PendingIntent.getBroadcast(act, id, Intent(ACTION_STATUS).setPackage(act.packageName), flags)
            s.commit(p.intentSender)
        }
        emit(state("inst"))
    }

    /** Resultado del instalador (lo recibe MainActivity). */
    fun onStatus(i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) act.startActivity(confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> emit(state("ok"))
            PackageInstaller.STATUS_FAILURE_ABORTED -> emit(state("new").put("v", version).put("size", apkSize))
            PackageInstaller.STATUS_FAILURE_CONFLICT, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                emit(state("err").put("msg", "Esta versión está firmada con otra clave. Desinstala la app una vez e instala la nueva."))
            else -> emit(state("err").put("msg", i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Android no pudo instalarla."))
        }
    }

    companion object { const val ACTION_STATUS = "com.ignis.studio.INSTALL_STATUS" }
}

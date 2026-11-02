package com.ignis.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var updater: UpdateBridge
    private lateinit var llm: LlmBridge
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var ready = false

    // Selector de imágenes para "Toca un logo para ponerle el oficial" (función upl() del HTML)
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

    // Importar un modelo .gguf del teléfono (no tiene un tipo MIME propio: se aceptan todos)
    private val pickGguf = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> llm.onPicked(uri) }

    // Dictado por voz con el reconocedor del teléfono (no necesita permiso de micrófono para la app)
    private val voiceIn = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        web.evaluateJavascript("window.onVoice&&onVoice(${JSONObject.quote(text ?: "")})", null)
    }

    fun startVoice() {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES")
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe tu idea")
        try { voiceIn.launch(i) } catch (e: ActivityNotFoundException) {
            web.evaluateJavascript("window.onVoice&&onVoice(null)", null)
        }
    }

    // Guardar un diseño como archivo .html donde el usuario elija
    private var pendingHtml: String? = null
    private val createHtml = registerForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { uri ->
        val html = pendingHtml; pendingHtml = null
        var ok = false
        if (uri != null && html != null) ok = try {
            contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }; true
        } catch (e: Exception) { false }
        web.evaluateJavascript("window.onSaved&&onSaved(${if (uri == null) "null" else ok.toString()})", null)
    }

    fun saveHtml(name: String, html: String) {
        pendingHtml = html
        try { createHtml.launch(name) } catch (e: ActivityNotFoundException) {
            web.evaluateJavascript("window.onSaved&&onSaved(false)", null)
        }
    }

    fun pickModel() = try { pickGguf.launch(arrayOf("*/*")) } catch (e: ActivityNotFoundException) { llm.onPicked(null) }

    // Lo que se guardó o ancló en el visor de inspiración vuelve a la Biblioteca (y al cerebro)
    private val inspo = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val items = r.data?.getStringExtra("items")
        if (r.resultCode == Activity.RESULT_OK && items != null && items != "[]")
            web.evaluateJavascript("window.onRefSaved&&onRefSaved($items)", null)
    }

    // Resultado del instalador de actualizaciones
    private val installStatus = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) = updater.onStatus(i)
    }

    fun openInspo(url: String, name: String) {
        val u = Uri.parse(url)
        if (u.scheme?.lowercase() !in setOf("http", "https")) return
        inspo.launch(Intent(this, InspoActivity::class.java).putExtra("u", url).putExtra("n", name))
    }

    private fun openExternal(u: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, u).addCategory(Intent.CATEGORY_BROWSABLE))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No hay ninguna app para abrir este enlace", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Pantalla de bienvenida con el logo; se mantiene hasta que la interfaz carga (máx. 2,5 s)
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { !ready }
        window.decorView.postDelayed({ ready = true }, 2500)
        window.statusBarColor = Color.parseColor("#070918")
        window.navigationBarColor = Color.parseColor("#07080F")

        val refs = File(filesDir, "refs").apply { mkdirs() }
        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .addPathHandler("/refs/", WebViewAssetLoader.InternalStoragePathHandler(this, refs)) // capturas guardadas
            .build()

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#070918"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // localStorage para los logos subidos (ig_lg)
            settings.allowFileAccess = false
            webViewClient = object : WebViewClient() {
                // Solo se carga contenido propio de la app: así ningún script de fuera puede
                // llegar a los puentes nativos (IgnisBrain, IgnisUpdate…)
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest): WebResourceResponse? =
                    when {
                        r.url.host == APP_HOST -> loader.shouldInterceptRequest(r.url)
                        r.url.scheme == "data" || r.url.scheme == "blob" -> null // logos incrustados y subidos
                        // Tipografías de Google para la vista previa de los diseños (solo CSS y fuentes, sin scripts)
                        r.method == "GET" && r.url.scheme == "https" &&
                            (r.url.host == "fonts.googleapis.com" || r.url.host == "fonts.gstatic.com") -> null
                        else -> WebResourceResponse("text/plain", "utf-8", 403, "Bloqueado", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                    }
                // Cualquier enlace externo se abre en el visor, sin salir de la app
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val u = r.url
                    if (u.host == APP_HOST) return false
                    when (u.scheme?.lowercase()) {
                        "http", "https" -> openInspo(u.toString(), u.host ?: "")
                        else -> openExternal(u) // mailto:, tel:, intent:… van a la app que corresponda
                    }
                    return true
                }
                override fun onPageFinished(v: WebView, url: String) { ready = true }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(
                    v: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams
                ): Boolean {
                    fileCallback?.onReceiveValue(null)
                    fileCallback = cb
                    pickImage.launch("image/*")
                    return true
                }
            }
        }
        updater = UpdateBridge(this, web)
        web.addJavascriptInterface(DeviceBridge(this), "IgnisDevice")
        web.addJavascriptInterface(BrainBridge(this, refs), "IgnisBrain")
        web.addJavascriptInterface(HapticBridge(web), "IgnisHaptic")
        web.addJavascriptInterface(updater, "IgnisUpdate")
        web.addJavascriptInterface(WebBridge(this), "IgnisWeb")
        llm = LlmBridge(this, web)
        web.addJavascriptInterface(llm, "IgnisLLM")
        setContentView(web)

        ContextCompat.registerReceiver(
            this, installStatus, IntentFilter(UpdateBridge.ACTION_STATUS), ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Botón atrás de Android: vuelve a la pantalla anterior (historial por #hash)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://$APP_HOST/assets/index.html")
    }

    // Al volver del ajuste de permisos, la actualización sigue sola
    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.onResumeApp&&onResumeApp()", null)
    }

    override fun onDestroy() {
        unregisterReceiver(installStatus)
        if (isFinishing && ::llm.isInitialized) llm.release()
        if (::web.isInitialized) {
            web.removeAllViews()
            web.destroy()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    private companion object { const val APP_HOST = WebViewAssetLoader.DEFAULT_DOMAIN }
}

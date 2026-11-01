package com.ignis.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var updater: UpdateBridge
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var ready = false

    // Selector de imágenes para "Toca un logo para ponerle el oficial" (función upl() del HTML)
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
    }

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

    fun openInspo(url: String, name: String) =
        inspo.launch(Intent(this, InspoActivity::class.java).putExtra("u", url).putExtra("n", name))

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
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) =
                    loader.shouldInterceptRequest(r.url)
                // Cualquier enlace externo se abre en el visor, sin salir de la app
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    if (r.url.host == "appassets.androidplatform.net") return false
                    openInspo(r.url.toString(), r.url.host ?: ""); return true
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
        web.addJavascriptInterface(BrainBridge(this), "IgnisBrain")
        web.addJavascriptInterface(HapticBridge(web), "IgnisHaptic")
        web.addJavascriptInterface(updater, "IgnisUpdate")
        web.addJavascriptInterface(WebBridge(this), "IgnisWeb")
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
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    // Al volver del ajuste de permisos, la actualización sigue sola
    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.evaluateJavascript("window.onResumeApp&&onResumeApp()", null)
    }

    override fun onDestroy() {
        unregisterReceiver(installStatus)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }
}

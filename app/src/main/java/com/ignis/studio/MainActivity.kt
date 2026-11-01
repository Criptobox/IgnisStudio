package com.ignis.studio

import android.annotation.SuppressLint
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
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var ready = false

    // Selector de imágenes para "Toca un logo para ponerle el oficial" (función upl() del HTML)
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        fileCallback?.onReceiveValue(if (uri != null) arrayOf(uri) else null)
        fileCallback = null
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

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#070918"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true   // localStorage para los logos subidos (ig_lg)
            settings.allowFileAccess = false
            addJavascriptInterface(DeviceBridge(this@MainActivity), "IgnisDevice")
            addJavascriptInterface(BrainBridge(this@MainActivity), "IgnisBrain")
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) =
                    loader.shouldInterceptRequest(r.url)
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
        web.addJavascriptInterface(HapticBridge(web), "IgnisHaptic")
        setContentView(web)

        // Botón atrás de Android: vuelve a la pantalla anterior (historial por #hash)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (web.canGoBack()) web.goBack() else finish()
            }
        })

        if (savedInstanceState != null) web.restoreState(savedInstanceState)
        else web.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }
}

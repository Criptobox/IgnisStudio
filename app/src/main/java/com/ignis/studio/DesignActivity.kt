package com.ignis.studio

import android.graphics.Color
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Abre a pantalla completa un diseño convertido en app (acceso directo en la pantalla de inicio).
 * Solo muestra el HTML guardado: sin JavaScript ni acceso a archivos.
 */
class DesignActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID)?.takeIf { ID.matches(it) }
        val f = id?.let { File(File(filesDir, "apps"), "$it.html") }
        if (f == null || !f.exists()) { finish(); return }
        window.statusBarColor = Color.BLACK
        val web = WebView(this).apply {
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            setBackgroundColor(Color.WHITE)
            loadDataWithBaseURL("https://ignis.app/", f.readText(), "text/html", "utf-8", null)
        }
        setContentView(web)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
    }

    companion object {
        const val EXTRA_ID = "design_id"
        val ID = Regex("[A-Za-z0-9_-]{1,40}")
    }
}

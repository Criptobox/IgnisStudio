package com.ignis.studio

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.TextUtils
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Visor de páginas ancladas: navega por Dribbble, Behance, etc. dentro de la app.
 * "Guardar" captura lo que se ve y lo manda a Biblioteca ▸ Guardados; "Anclar" añade la página a Anclados.
 */
class InspoActivity : AppCompatActivity() {

    private lateinit var web: WebView
    private lateinit var titleView: TextView
    private val results = JSONArray()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun chip(text: String, accent: Boolean, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(14), dp(8), dp(14), dp(8)); gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            cornerRadius = dp(18).toFloat()
            if (accent) colors = intArrayOf(Color.parseColor("#9A63FF"), Color.parseColor("#7440F0"))
            else setColor(Color.parseColor("#1D1E2B"))
        }
        setOnClickListener { onClick() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent.getStringExtra("u") ?: "https://dribbble.com"
        window.statusBarColor = Color.parseColor("#0E0F18")

        titleView = TextView(this).apply {
            text = intent.getStringExtra("n") ?: ""
            setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.DEFAULT_BOLD
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(10), 0, dp(6), 0)
        }
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8)); setBackgroundColor(Color.parseColor("#0E0F18"))
            addView(chip("✕", false) { finishWithResults() })
            addView(titleView, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            addView(chip("Anclar", false) { pinPage() })
            addView(LinearLayout(context), LinearLayout.LayoutParams(dp(6), 1))
            addView(chip("♥ Guardar", true) { capture() })
        }
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        web = WebView(this).apply {
            setBackgroundColor(Color.parseColor("#070918"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            webViewClient = WebViewClient()
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(v: WebView, p: Int) { progress.progress = p; progress.alpha = if (p < 100) 1f else 0f }
                override fun onReceivedTitle(v: WebView, t: String?) { if (!t.isNullOrBlank()) titleView.text = t }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.parseColor("#070918"))
            addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            addView(progress, LinearLayout.LayoutParams(MATCH_PARENT, dp(3)))
            addView(web, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finishWithResults() }
        })
        if (savedInstanceState == null) web.loadUrl(startUrl) else web.restoreState(savedInstanceState)
    }

    /** Captura lo visible, la reduce y la guarda en files/refs para que la Biblioteca la muestre. */
    private fun capture() {
        if (web.width == 0 || web.height == 0) return
        val full = Bitmap.createBitmap(web.width, web.height, Bitmap.Config.ARGB_8888)
        web.draw(Canvas(full))
        val w = 540; val h = (full.height * w.toFloat() / full.width).toInt()
        val small = Bitmap.createScaledBitmap(full, w, h, true)
        val id = System.currentTimeMillis().toString()
        val dir = File(filesDir, "refs").apply { mkdirs() }
        File(dir, "ref_$id.jpg").outputStream().use { small.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        full.recycle(); small.recycle()
        results.put(JSONObject().put("t", "ref").put("id", id).put("n", web.title ?: titleView.text.toString()).put("u", web.url).put("img", "/refs/ref_$id.jpg"))
        web.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
        Toast.makeText(this, "Guardado en Biblioteca", Toast.LENGTH_SHORT).show()
    }

    private fun pinPage() {
        val u = web.url ?: return
        results.put(JSONObject().put("t", "pin").put("n", (web.title ?: u).take(40)).put("u", u))
        Toast.makeText(this, "Página anclada", Toast.LENGTH_SHORT).show()
    }

    private fun finishWithResults() {
        setResult(Activity.RESULT_OK, Intent().putExtra("items", results.toString()))
        finish()
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); web.saveState(outState) }
}

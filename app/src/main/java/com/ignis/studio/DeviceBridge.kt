package com.ignis.studio

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import android.webkit.JavascriptInterface
import org.json.JSONObject

/** Expone al HTML las propiedades reales del teléfono como window.IgnisDevice.info() */
class DeviceBridge(private val ctx: Context) {
    private fun gb(b: Long) = Math.round(b / 1e9 * 10) / 10.0

    @JavascriptInterface
    fun info(): String {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val fs = StatFs(ctx.filesDir.absolutePath)
        val soc = if (Build.VERSION.SDK_INT >= 31) "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}" else Build.HARDWARE
        return JSONObject()
            .put("name", "${Build.MANUFACTURER} ${Build.MODEL}")
            .put("soc", soc)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "")
            .put("cores", Runtime.getRuntime().availableProcessors())
            .put("ram", Math.round(mi.totalMem / 1e9).toInt())
            .put("free", gb(mi.availMem))
            .put("disk", gb(fs.availableBytes))
            .put("dt", gb(fs.totalBytes))
            .toString()
    }
}

package com.example.mawaqiti

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private val req = 77

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = true
            settings.allowContentAccess = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = WebViewClient()
            addJavascriptInterface(AndroidBridge(this@MainActivity), "Android")
            loadUrl("file:///android_asset/index.html")
        }
        setContentView(web)
        requestPermissionsAndStart()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.postDelayed({ web.evaluateJavascript("window.nativeResume && window.nativeResume()", null) }, 250)
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsAndStart() {
        val list = mutableListOf<String>()
        if (!hasLocation()) {
            list += Manifest.permission.ACCESS_FINE_LOCATION
            list += Manifest.permission.ACCESS_COARSE_LOCATION
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            list += Manifest.permission.POST_NOTIFICATIONS
        }
        if (list.isNotEmpty()) ActivityCompat.requestPermissions(this, list.distinct().toTypedArray(), req)
        else startPrayerService()
    }

    private fun startPrayerService() {
        if (hasLocation()) {
            try { ContextCompat.startForegroundService(this, Intent(this, PrayerService::class.java)) } catch (_: Exception) {}
        }
        requestExactAlarmIfNeeded()
    }

    private fun requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 32) {
            val am = getSystemService(AlarmManager::class.java)
            if (!am.canScheduleExactAlarms()) {
                try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == req) {
            if (hasLocation()) startPrayerService()
            else {
                getSharedPreferences("times", 0).edit().putString("error", "لم يتم السماح بالموقع").apply()
                web.post { web.evaluateJavascript("window.nativeResume && window.nativeResume()", null) }
            }
        }
    }

    inner class AndroidBridge(private val ctx: Context) {
        @JavascriptInterface fun getPrayerData(): String {
            val p = ctx.getSharedPreferences("times", 0)
            return JSONObject().apply {
                put("timings", JSONObject(p.getString("json", "{}") ?: "{}"))
                put("lat", p.getFloat("lat", 0f).toDouble())
                put("lon", p.getFloat("lon", 0f).toDouble())
                put("updated", p.getLong("updated", 0L))
                put("error", p.getString("error", "") ?: "")
                put("source", p.getString("source", "") ?: "")
                put("exact", canExact())
                put("locationPermission", hasLocation())
            }.toString()
        }

        @JavascriptInterface fun refresh() {
            runOnUiThread { requestPermissionsAndStart() }
        }

        @JavascriptInterface fun testAdhan() {
            try { ContextCompat.startForegroundService(ctx, Intent(ctx, AdhanService::class.java).setAction("TEST").putExtra("name", "اختبار")) } catch (_: Exception) {}
        }

        @JavascriptInterface fun setVolume(value: Int) {
            ctx.getSharedPreferences("settings", 0).edit().putInt("vol", value.coerceIn(0, 100)).apply()
        }

        @JavascriptInterface fun openExactAlarmSettings() {
            if (Build.VERSION.SDK_INT >= 31 && Build.VERSION.SDK_INT <= 32) requestExactAlarmIfNeeded()
            else if (Build.VERSION.SDK_INT >= 33) {
                try { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName"))) } catch (_: Exception) {}
            }
        }

        private fun canExact(): Boolean = when {
            Build.VERSION.SDK_INT >= 33 -> true // USE_EXACT_ALARM is declared for the core prayer-alarm use case.
            Build.VERSION.SDK_INT >= 31 -> getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
            else -> true
        }
    }
}

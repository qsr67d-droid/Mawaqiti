package com.example.mawaqiti

import android.content.*
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "ADHAN" -> {
                val name = intent.getStringExtra("name") ?: "الصلاة"
                try {
                    ContextCompat.startForegroundService(context, Intent(context, AdhanService::class.java).setAction("PLAY").putExtra("name", name))
                } catch (_: Exception) {}
            }
            "REFRESH" -> refresh(context)
        }
    }

    private fun refresh(context: Context) {
        val p = context.getSharedPreferences("times", 0)
        val lat = p.getFloat("lat", 0f).toDouble(); val lon = p.getFloat("lon", 0f).toDouble()
        if (lat == 0.0 && lon == 0.0) return
        Thread {
            var timings: JSONObject? = null
            var source = ""
            try {
                val d = java.text.SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
                val url = "https://api.aladhan.com/v1/timings/$d?latitude=$lat&longitude=$lon&method=5&school=0"
                val conn = java.net.URL(url).openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 12_000; conn.readTimeout = 12_000; conn.requestMethod = "GET"; conn.useCaches = false
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
                conn.disconnect()
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val root = JSONObject(body)
                if (root.optInt("code", -1) != 200) throw IllegalStateException("API error")
                timings = root.getJSONObject("data").getJSONObject("timings")
                source = "AlAdhan"
            } catch (_: Exception) {
                try { timings = PrayerCalculator.calculate(Calendar.getInstance(), lat, lon); source = "offline" } catch (_: Exception) {}
            }
            timings?.let {
                p.edit().putString("json", it.toString()).putLong("updated", System.currentTimeMillis()).putString("source", source)
                    .putString("error", if (source == "offline") "تم استخدام الحساب المحلي" else "").apply()
                AlarmScheduler.scheduleAll(context, it)
            }
        }.start()
    }
}

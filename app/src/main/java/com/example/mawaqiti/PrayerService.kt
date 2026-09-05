package com.example.mawaqiti

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class PrayerService : Service() {
    private lateinit var client: FusedLocationProviderClient
    private var finished = false

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("mawaqiti", "مواقيتي", NotificationManager.IMPORTANCE_LOW))
        val n = NotificationCompat.Builder(this, "mawaqiti")
            .setContentTitle("مواقيتي")
            .setContentText("جاري تحديث الموقع ومواقيت الصلاة")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .build()
        startForeground(10, n)
        client = LocationServices.getFusedLocationProviderClient(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        finished = false
        requestLocationAndFetch()
        return START_NOT_STICKY
    }

    private fun requestLocationAndFetch() {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) { fail("لم يتم السماح بالموقع"); return }

        client.lastLocation
            .addOnSuccessListener { loc ->
                if (loc != null && System.currentTimeMillis() - loc.time < 6 * 60 * 60 * 1000L) {
                    fetch(loc.latitude, loc.longitude)
                } else requestFreshLocation()
            }
            .addOnFailureListener { requestFreshLocation() }
    }

    private fun requestFreshLocation() {
        try {
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3_000L)
                .setMinUpdateIntervalMillis(1_000L)
                .setMaxUpdates(1)
                .setWaitForAccurateLocation(false)
                .build()
            val cb = object : LocationCallback() {
                override fun onLocationResult(r: LocationResult) {
                    client.removeLocationUpdates(this)
                    val loc = r.lastLocation
                    if (loc != null) fetch(loc.latitude, loc.longitude) else fail("تعذر الحصول على إحداثيات GPS")
                }
            }
            client.requestLocationUpdates(req, cb, Looper.getMainLooper())
                .addOnFailureListener { client.removeLocationUpdates(cb); fail("تعذر تشغيل GPS") }
        } catch (_: Exception) { fail("تعذر تشغيل GPS") }
    }

    private fun fetch(lat: Double, lon: Double) {
        Thread {
            var result: JSONObject? = null
            var source = ""
            var error = ""
            try {
                result = fetchFromApi(lat, lon)
                source = "AlAdhan"
            } catch (e: Exception) {
                error = e.message ?: "فشل الاتصال بخدمة مواقيت الصلاة"
                try {
                    result = PrayerCalculator.calculate(Calendar.getInstance(), lat, lon)
                    source = "offline"
                } catch (fallback: Exception) {
                    error = "تعذر جلب المواقيت: ${fallback.message ?: error}"
                }
            }
            if (result != null) {
                val p = getSharedPreferences("times", 0)
                p.edit()
                    .putString("json", result.toString())
                    .putLong("updated", System.currentTimeMillis())
                    .putFloat("lat", lat.toFloat()).putFloat("lon", lon.toFloat())
                    .putString("source", source)
                    .putString("error", if (source == "offline") "تم استخدام الحساب المحلي لأن الاتصال بالخدمة غير متاح" else "")
                    .apply()
                AlarmScheduler.scheduleAll(this, result!!)
            } else fail(error)
            finishOnce()
        }.start()
    }

    private fun fetchFromApi(lat: Double, lon: Double): JSONObject {
        val d = SimpleDateFormat("dd-MM-yyyy", Locale.US).format(Date())
        val endpoint = "https://api.aladhan.com/v1/timings/$d?latitude=$lat&longitude=$lon&method=5&school=0"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "Mawaqiti/3.0 Android")
        }
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            val root = JSONObject(body)
            if (root.optInt("code", -1) != 200) throw IllegalStateException("AlAdhan error ${root.optInt("code")}")
            val timings = root.getJSONObject("data").getJSONObject("timings")
            if (timings.optString("Fajr").isBlank() || timings.optString("Isha").isBlank()) throw IllegalStateException("بيانات المواقيت غير مكتملة")
            return timings
        } finally { conn.disconnect() }
    }

    private fun fail(message: String) {
        getSharedPreferences("times", 0).edit().putString("error", message).apply()
        finishOnce()
    }

    private fun finishOnce() { if (!finished) { finished = true; stopSelf() } }
    override fun onBind(intent: Intent?) = null
}

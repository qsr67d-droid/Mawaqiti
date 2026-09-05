package com.example.mawaqiti

import android.app.*
import android.content.*
import android.os.Build
import org.json.JSONObject
import java.util.*

object AlarmScheduler {
    private val prayerNames = listOf("Fajr", "Dhuhr", "Asr", "Maghrib", "Isha")
    private val ids = mapOf("Fajr" to 101, "Dhuhr" to 102, "Asr" to 103, "Maghrib" to 104, "Isha" to 105)
    private const val refreshId = 199

    fun scheduleAll(context: Context, timings: JSONObject) {
        val am = context.getSystemService(AlarmManager::class.java)
        val now = System.currentTimeMillis()
        prayerNames.forEach { name ->
            val hm = timings.optString(name, "")
            val t = parseToday(hm) ?: return@forEach
            val intent = Intent(context, AlarmReceiver::class.java).setAction("ADHAN").putExtra("name", name)
            val pi = PendingIntent.getBroadcast(context, ids[name]!!, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            am.cancel(pi)
            if (t.timeInMillis > now + 1_000) setAlarm(am, t.timeInMillis, pi)
        }

        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 5); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val ri = Intent(context, AlarmReceiver::class.java).setAction("REFRESH")
        val rpi = PendingIntent.getBroadcast(context, refreshId, ri, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(rpi)
        setAlarm(am, midnight.timeInMillis, rpi)
    }

    private fun setAlarm(am: AlarmManager, trigger: Long, pi: PendingIntent) {
        val exact = canExact(am)
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else am.setExact(AlarmManager.RTC_WAKEUP, trigger, pi)
        } catch (_: SecurityException) {
            if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            else am.set(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun canExact(am: AlarmManager): Boolean = if (Build.VERSION.SDK_INT >= 31) am.canScheduleExactAlarms() else true

    private fun parseToday(hm: String): Calendar? {
        val clean = hm.substringBefore(" ").trim()
        val parts = clean.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
    }
}

package com.example.mawaqiti

import android.content.*
import org.json.JSONObject

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> {
                val json = context.getSharedPreferences("times", 0).getString("json", null)
                if (!json.isNullOrBlank()) try { AlarmScheduler.scheduleAll(context, JSONObject(json)) } catch (_: Exception) {}
            }
        }
    }
}

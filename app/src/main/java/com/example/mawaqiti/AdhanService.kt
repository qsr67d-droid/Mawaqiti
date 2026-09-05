package com.example.mawaqiti

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.media.*
import android.os.*
import androidx.core.app.NotificationCompat

class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("adhan", "الأذان", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(null, null)
        })
        val n = NotificationCompat.Builder(this, "adhan")
            .setContentTitle("مواقيتي")
            .setContentText("حان وقت الأذان")
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(false).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(20, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else startForeground(20, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int { play(); return START_NOT_STICKY }

    private fun play() {
        player?.release()
        val volume = getSharedPreferences("settings", 0).getInt("vol", 20).coerceIn(0, 100) / 100f
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        val am = getSystemService(AudioManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                .setAudioAttributes(attrs).setWillPauseWhenDucked(false).build()
            am.requestAudioFocus(focus!!)
        }
        player = MediaPlayer.create(this, R.raw.adhan)?.also { mp ->
            mp.setAudioAttributes(attrs)
            mp.setVolume(volume, volume)
            mp.setOnCompletionListener { stopSelf() }
            mp.setOnErrorListener { _, _, _ -> stopSelf(); true }
            mp.start()
        }
    }

    override fun onDestroy() {
        player?.release(); player = null
        if (Build.VERSION.SDK_INT >= 26) focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focus = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}

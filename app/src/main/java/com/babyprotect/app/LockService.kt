package com.babyprotect.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat

class LockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var audioManager: AudioManager
    private val CHANNEL_ID = "BabyProtectChannel"
    private val CHECK_INTERVAL = 2000L // check every 2 seconds

    private val enforceRunnable = object : Runnable {
        override fun run() {
            val prefs = getSharedPreferences("BabyProtect", Context.MODE_PRIVATE)
            val isLocked = prefs.getBoolean("locked", false)

            if (isLocked) {
                enforceLimits(prefs)
            } else {
                stopSelf()
                return
            }

            handler.postDelayed(this, CHECK_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(1, buildNotification())
        handler.post(enforceRunnable)
        return START_STICKY
    }

    private fun enforceLimits(prefs: android.content.SharedPreferences) {
        val brightnessPercent = prefs.getInt("brightness", 25)
        val volumePercent = prefs.getInt("volume", 25)

        // Enforce brightness
        if (Settings.System.canWrite(this)) {
            val brightnessValue = (brightnessPercent / 100f * 255).toInt().coerceIn(1, 255)
            try {
                val currentBrightness = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                if (currentBrightness > brightnessValue) {
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, brightnessValue)
                }
            } catch (e: Exception) { }
        }

        // Enforce volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumeValue = (volumePercent / 100f * maxVolume).toInt().coerceIn(0, maxVolume)

        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION
        )
        for (stream in streams) {
            val streamMax = audioManager.getStreamMaxVolume(stream)
            val streamValue = (volumePercent / 100f * streamMax).toInt().coerceIn(0, streamMax)
            if (audioManager.getStreamVolume(stream) > streamValue) {
                audioManager.setStreamVolume(stream, streamValue, 0)
            }
        }
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("👶 Baby Protect Active")
            .setContentText("Brightness & Volume are locked")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Baby Protect",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Baby screen protection service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(enforceRunnable)
        // When service is destroyed (app uninstalled), lock is automatically released
        // because SharedPreferences are deleted with the app
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

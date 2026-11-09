package com.alertme.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

/** تشغيل النغمة والاهتزاز مستقل عن أي شاشة، عشان الصوت يشتغل حتى لو الشاشة ما اتفتحتش. */
object AlertPlayer {
    private const val AUTO_STOP_MS = 120_000L

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stop() }

    @Synchronized
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
        if (player != null) return

        var mp: MediaPlayer? = null
        try {
            mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            val f = Prefs.soundFile(app)
            if (f.exists() && f.length() > 0) {
                mp.setDataSource(f.absolutePath)
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mp.setDataSource(app, uri)
            }
            mp.isLooping = true
            mp.prepare()
            mp.start()
            player = mp
        } catch (e: Exception) {
            try {
                mp?.release()
            } catch (_: Exception) {
            }
            player = null
        }
        startVibration(app)
    }

    @Suppress("DEPRECATION")
    private fun startVibration(app: Context) {
        try {
            val v = app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator = v
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (e: Exception) {
            // الاهتزاز اختياري
        }
    }

    @Synchronized
    fun stop() {
        handler.removeCallbacks(autoStop)
        try {
            player?.let {
                try {
                    if (it.isPlaying) it.stop()
                } catch (_: Exception) {
                }
                it.release()
            }
        } catch (_: Exception) {
        }
        player = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {
        }
        vibrator = null
    }
}

package com.alertme.app

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** بيطلّع التنبيه: إشعار full-screen + فتح الشاشة مباشرة لو صلاحية "العرض فوق التطبيقات" متاحة. */
object AlertLauncher {
    const val CHANNEL_ID = "alertme_alerts"
    const val NOTIF_ID = 4242

    fun show(ctx: Context, header: String, source: String, preview: String) {
        val intent = Intent(ctx, AlertActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(AlertActivity.EXTRA_HEADER, header)
        intent.putExtra(AlertActivity.EXTRA_SOURCE, source)
        intent.putExtra(AlertActivity.EXTRA_PREVIEW, preview)

        postNotification(ctx, intent, header, preview)

        if (Settings.canDrawOverlays(ctx)) {
            try {
                ctx.startActivity(intent)
            } catch (e: Exception) {
                // الإشعار full-screen هيغطي الحالة دي
            }
        }
    }

    private fun postNotification(ctx: Context, intent: Intent, header: String, preview: String) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(CHANNEL_ID, "تنبيهات الرسائل المهمة", NotificationManager.IMPORTANCE_HIGH)
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            nm.createNotificationChannel(channel)

            val pi = PendingIntent.getActivity(
                ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notif = android.app.Notification.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(header)
                .setContentText(preview)
                .setCategory(android.app.Notification.CATEGORY_ALARM)
                .setContentIntent(pi)
                .setFullScreenIntent(pi, true)
                .setAutoCancel(true)
                .build()
            nm.notify(NOTIF_ID, notif)
        } catch (e: Exception) {
            // لو الإذن مش ممنوح الإشعار مش هيظهر، لكن الشاشة ممكن تفتح بصلاحية العرض فوق التطبيقات
        }
    }
}

class AlertActivity : Activity() {

    companion object {
        const val EXTRA_HEADER = "header"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_PREVIEW = "preview"
        private const val AUTO_STOP_MS = 120_000L
    }

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { dismissAlert() }

    private lateinit var headerView: TextView
    private lateinit var sourceView: TextView
    private lateinit var previewView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_ALARM
        showOverLockscreen()
        buildUi()
        bind(intent)
        startAlarm()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        bind(intent)
        startAlarm()
    }

    override fun onDestroy() {
        stopAlarm()
        handler.removeCallbacks(autoStop)
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockscreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.parseColor("#101418"))
        root.setPadding(dp(24), dp(40), dp(24), dp(32))
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        // الصورة في منتصف الشاشة
        val image = ImageView(this)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.adjustViewBounds = true
        val bmp = ImageUtil.load(Prefs.imageFile(this), 1400)
        if (bmp != null) {
            image.setImageBitmap(bmp)
        } else {
            image.setImageResource(R.drawable.ic_launcher_foreground)
        }
        root.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        headerView = TextView(this)
        headerView.setTextColor(Color.WHITE)
        headerView.textSize = 26f
        headerView.setTypeface(headerView.typeface, Typeface.BOLD)
        headerView.gravity = Gravity.CENTER
        root.addView(headerView, matchWrap(top = 16))

        sourceView = TextView(this)
        sourceView.setTextColor(Color.parseColor("#B0BEC5"))
        sourceView.textSize = 16f
        sourceView.gravity = Gravity.CENTER
        root.addView(sourceView, matchWrap(top = 4))

        previewView = TextView(this)
        previewView.setTextColor(Color.parseColor("#ECEFF1"))
        previewView.textSize = 18f
        previewView.gravity = Gravity.CENTER
        previewView.maxLines = 4
        root.addView(previewView, matchWrap(top = 12))

        val ok = Button(this)
        ok.text = "موافق"
        ok.textSize = 22f
        ok.setTextColor(Color.WHITE)
        ok.isAllCaps = false
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#D32F2F"))
        bg.cornerRadius = dp(16).toFloat()
        ok.background = bg
        ok.setOnClickListener { dismissAlert() }
        val okParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        okParams.topMargin = dp(24)
        root.addView(ok, okParams)

        setContentView(root)
    }

    private fun matchWrap(top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(top)
        return lp
    }

    private fun bind(i: Intent?) {
        headerView.text = i?.getStringExtra(EXTRA_HEADER) ?: "تنبيه"
        sourceView.text = i?.getStringExtra(EXTRA_SOURCE) ?: ""
        previewView.text = i?.getStringExtra(EXTRA_PREVIEW) ?: ""
    }

    private fun startAlarm() {
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
            val f = Prefs.soundFile(this)
            if (f.exists() && f.length() > 0) {
                mp.setDataSource(f.absolutePath)
            } else {
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                mp.setDataSource(this, uri)
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
        startVibration()
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
            vibrator = v
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 700, 500), 0))
        } catch (e: Exception) {
            // الاهتزاز اختياري
        }
    }

    private fun stopAlarm() {
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

    private fun dismissAlert() {
        stopAlarm()
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(AlertLauncher.NOTIF_ID)
        } catch (_: Exception) {
        }
        finishAndRemoveTask()
    }
}

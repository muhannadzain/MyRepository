package com.alertme.app

import android.app.Activity
import android.app.KeyguardManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.WindowManager

/**
 * بيطلّع التنبيه:
 *  - الصوت دايمًا (مستقل عن أي شاشة)
 *  - إشعار full-screen (للقفل)
 *  - نافذة overlay فوق التطبيقات لو الموبايل مفتوح، أو Activity فوق شاشة القفل
 */
object AlertLauncher {
    const val CHANNEL_ID = "alertme_alerts"
    const val NOTIF_ID = 4242

    fun show(ctx: Context, header: String, source: String, preview: String) {
        val app = ctx.applicationContext
        val intent = Intent(app, AlertActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        intent.putExtra(AlertActivity.EXTRA_HEADER, header)
        intent.putExtra(AlertActivity.EXTRA_SOURCE, source)
        intent.putExtra(AlertActivity.EXTRA_PREVIEW, preview)

        AlertPlayer.start(app)
        postNotification(app, intent, header, preview)

        val canOverlay = Settings.canDrawOverlays(app)
        val km = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        if (canOverlay && !km.isKeyguardLocked) {
            wakeScreen(app)
            AlertOverlay.show(app, header, source, preview) { startActivityQuietly(app, intent) }
        } else if (canOverlay) {
            startActivityQuietly(app, intent)
        }
    }

    fun cancelNotification(ctx: Context) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIF_ID)
        } catch (_: Exception) {
        }
    }

    private fun startActivityQuietly(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            // الإشعار full-screen هيغطي الحالة دي
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen(ctx: Context) {
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                val wl = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "alertme:wake"
                )
                wl.acquire(10_000L)
            }
        } catch (e: Exception) {
            // اختياري
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
            // لو إذن الإشعارات مش ممنوح، الصوت والـ overlay لسه شغالين
        }
    }
}

class AlertActivity : Activity() {

    companion object {
        const val EXTRA_HEADER = "header"
        const val EXTRA_SOURCE = "source"
        const val EXTRA_PREVIEW = "preview"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_ALARM
        showOverLockscreen()
        render(intent)
        AlertPlayer.start(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        render(intent)
        AlertPlayer.start(this)
    }

    override fun onDestroy() {
        if (isFinishing) AlertPlayer.stop()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        dismissAlert()
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

    private fun render(i: Intent?) {
        setContentView(
            AlertView.create(
                this,
                i?.getStringExtra(EXTRA_HEADER) ?: "تنبيه",
                i?.getStringExtra(EXTRA_SOURCE) ?: "",
                i?.getStringExtra(EXTRA_PREVIEW) ?: ""
            ) { dismissAlert() }
        )
    }

    private fun dismissAlert() {
        AlertPlayer.stop()
        AlertOverlay.dismiss(this)
        AlertLauncher.cancelNotification(this)
        finishAndRemoveTask()
    }
}

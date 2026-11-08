package com.alertme.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** بعد إعادة تشغيل الموبايل، بيرجّع مراقبة تلجرام لو كانت شغالة. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED && Prefs.tgEnabled(context)) {
            try {
                context.startForegroundService(Intent(context, TelegramService::class.java))
            } catch (e: Exception) {
                // لو النظام منع التشغيل، هتشتغل أول ما تفتح التطبيق
            }
        }
    }
}

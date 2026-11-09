package com.alertme.app

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager

/**
 * نافذة بملء الشاشة فوق أي تطبيق (بإذن "العرض فوق التطبيقات").
 * بتشتغل من الخلفية من غير ما نحتاج نفتح Activity (أندرويد بيمنع ده).
 */
object AlertOverlay {
    private val handler = Handler(Looper.getMainLooper())
    private var view: View? = null

    fun show(ctx: Context, header: String, source: String, preview: String, onFail: () -> Unit) {
        val app = ctx.applicationContext
        handler.post {
            try {
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                removeNow(wm)
                val v = AlertView.create(app, header, source, preview) {
                    AlertPlayer.stop()
                    AlertLauncher.cancelNotification(app)
                    dismiss(app)
                }
                val lp = WindowManager.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                    PixelFormat.OPAQUE
                )
                wm.addView(v, lp)
                view = v
            } catch (e: Throwable) {
                view = null
                try {
                    onFail()
                } catch (_: Throwable) {
                }
            }
        }
    }

    fun dismiss(ctx: Context) {
        val app = ctx.applicationContext
        handler.post {
            try {
                val wm = app.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                removeNow(wm)
            } catch (_: Throwable) {
            }
        }
    }

    private fun removeNow(wm: WindowManager) {
        val v = view
        view = null
        if (v != null) {
            try {
                wm.removeView(v)
            } catch (_: Throwable) {
            }
        }
    }
}

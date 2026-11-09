package com.alertme.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** واجهة التنبيه (الصورة في النص + النص + زر موافق) — بتتستخدم في الـ Activity وفي نافذة الـ overlay. */
object AlertView {

    fun create(ctx: Context, header: String, source: String, preview: String, onOk: () -> Unit): View {
        val density = ctx.resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        val root = LinearLayout(ctx)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.parseColor("#101418"))
        root.setPadding(dp(24), dp(48), dp(24), dp(32))
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL
        root.isClickable = true // يمنع الضغط من النفاذ للتطبيق اللي تحت

        val image = ImageView(ctx)
        image.scaleType = ImageView.ScaleType.FIT_CENTER
        image.adjustViewBounds = true
        val bmp = ImageUtil.load(Prefs.imageFile(ctx), 1400)
        if (bmp != null) {
            image.setImageBitmap(bmp)
        } else {
            image.setImageResource(R.drawable.ic_launcher_foreground)
        }
        root.addView(image, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fun text(t: String, size: Float, color: String, bold: Boolean, top: Int, maxLines: Int): TextView {
            val tv = TextView(ctx)
            tv.text = t
            tv.textSize = size
            tv.setTextColor(Color.parseColor(color))
            tv.gravity = Gravity.CENTER
            if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
            if (maxLines > 0) tv.maxLines = maxLines
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.topMargin = dp(top)
            root.addView(tv, lp)
            return tv
        }

        text(header, 26f, "#FFFFFF", true, 16, 0)
        text(source, 16f, "#B0BEC5", false, 4, 0)
        text(preview, 18f, "#ECEFF1", false, 12, 4)

        val ok = Button(ctx)
        ok.text = "موافق"
        ok.textSize = 22f
        ok.setTextColor(Color.WHITE)
        ok.isAllCaps = false
        val bg = GradientDrawable()
        bg.setColor(Color.parseColor("#D32F2F"))
        bg.cornerRadius = dp(16).toFloat()
        ok.background = bg
        ok.setOnClickListener { onOk() }
        val okParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        okParams.topMargin = dp(24)
        root.addView(ok, okParams)

        return root
    }
}

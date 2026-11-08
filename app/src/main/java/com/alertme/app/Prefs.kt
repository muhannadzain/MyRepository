package com.alertme.app

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** قاعدة مراقبة: شخص (واختياريًا جروب) على واتساب و/أو تلجرام. */
data class Rule(
    val person: String,          // اسم / @يوزرنيم / رقم موبايل
    val group: String,           // اسم الجروب (مستخدم مع SCOPE_GROUP فقط)
    val whatsapp: Boolean,
    val telegram: Boolean,
    val scope: Int = 0           // SCOPE_ANY
) {
    companion object {
        const val SCOPE_ANY = 0      // أي مكان
        const val SCOPE_PRIVATE = 1  // الخاص فقط
        const val SCOPE_GROUP = 2    // جروب محدد فقط
    }
}

object Prefs {
    private const val FILE = "alertme_prefs"
    private const val KEY_RULES = "rules"
    private const val KEY_SOUND_NAME = "sound_name"
    private const val IMAGE_FILE = "alert_image"
    private const val SOUND_FILE = "alert_sound"

    private fun sp(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun getRules(ctx: Context): MutableList<Rule> {
        val list = mutableListOf<Rule>()
        val raw = sp(ctx).getString(KEY_RULES, null) ?: return list
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val group = o.optString("group")
                // القواعد القديمة: لو فيها جروب يبقى نطاقها جروب محدد
                val scope = if (o.has("scope")) {
                    o.optInt("scope", Rule.SCOPE_ANY)
                } else if (group.isNotBlank()) {
                    Rule.SCOPE_GROUP
                } else {
                    Rule.SCOPE_ANY
                }
                list.add(
                    Rule(
                        o.optString("person"),
                        group,
                        o.optBoolean("wa", true),
                        o.optBoolean("tg", true),
                        scope
                    )
                )
            }
        } catch (e: Exception) {
            // تجاهل البيانات التالفة
        }
        return list
    }

    fun saveRules(ctx: Context, rules: List<Rule>) {
        val arr = JSONArray()
        for (r in rules) {
            val o = JSONObject()
            o.put("person", r.person)
            o.put("group", r.group)
            o.put("wa", r.whatsapp)
            o.put("tg", r.telegram)
            o.put("scope", r.scope)
            arr.put(o)
        }
        sp(ctx).edit().putString(KEY_RULES, arr.toString()).apply()
    }

    // ---- تلجرام (اتصال مباشر) ----
    private const val KEY_TG_ENABLED = "tg_enabled"
    private const val KEY_TG_API_ID = "tg_api_id"
    private const val KEY_TG_API_HASH = "tg_api_hash"

    fun tgEnabled(ctx: Context): Boolean = sp(ctx).getBoolean(KEY_TG_ENABLED, false)

    fun setTgEnabled(ctx: Context, v: Boolean) {
        sp(ctx).edit().putBoolean(KEY_TG_ENABLED, v).apply()
    }

    fun tgApiId(ctx: Context): Int = sp(ctx).getInt(KEY_TG_API_ID, 0)

    fun tgApiHash(ctx: Context): String = sp(ctx).getString(KEY_TG_API_HASH, "") ?: ""

    fun setTgCredentials(ctx: Context, id: Int, hash: String) {
        sp(ctx).edit().putInt(KEY_TG_API_ID, id).putString(KEY_TG_API_HASH, hash).apply()
    }

    fun imageFile(ctx: Context): File = File(ctx.filesDir, IMAGE_FILE)

    fun soundFile(ctx: Context): File = File(ctx.filesDir, SOUND_FILE)

    fun getSoundName(ctx: Context): String? = sp(ctx).getString(KEY_SOUND_NAME, null)

    fun setSoundName(ctx: Context, name: String?) {
        sp(ctx).edit().putString(KEY_SOUND_NAME, name).apply()
    }
}

object ImageUtil {
    /** تحميل صورة بحجم مناسب عشان ما نستهلكش ذاكرة. */
    fun load(file: File, maxSize: Int): Bitmap? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val bounds = BitmapFactory.Options()
            bounds.inJustDecodeBounds = true
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            var sample = 1
            while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
                sample *= 2
            }
            val opts = BitmapFactory.Options()
            opts.inSampleSize = sample
            BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Throwable) {
            null
        }
    }
}

object TextNorm {
    /** توحيد النص العربي/الإنجليزي للمقارنة (تشكيل، همزات، تاء مربوطة...). */
    fun normalize(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s.lowercase()) {
            when {
                ch in 'ً'..'ٟ' || ch == 'ـ' -> { /* تشكيل / تطويل */ }
                ch in '‎'..'‏' || ch in '‪'..'‮' || ch in '⁦'..'⁩' -> { /* علامات اتجاه */ }
                ch == 'أ' || ch == 'إ' || ch == 'آ' -> sb.append('ا')
                ch == 'ة' -> sb.append('ه')
                ch == 'ى' -> sb.append('ي')
                else -> sb.append(ch)
            }
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }
}

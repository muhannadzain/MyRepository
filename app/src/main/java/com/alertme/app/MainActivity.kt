package com.alertme.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {

    private val reqImage = 1
    private val reqSound = 2
    private val reqNotif = 3

    private val match = ViewGroup.LayoutParams.MATCH_PARENT
    private val wrap = ViewGroup.LayoutParams.WRAP_CONTENT

    private lateinit var statusView: TextView
    private lateinit var previewImage: ImageView
    private lateinit var soundLabel: TextView
    private lateinit var rulesContainer: LinearLayout
    private lateinit var tgStatus: TextView
    private lateinit var tgLoginBtn: Button
    private lateinit var tgLogoutBtn: Button

    private var phoneDialog: AlertDialog? = null
    private var codeDialog: AlertDialog? = null
    private var passDialog: AlertDialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), reqNotif)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
        TelegramManager.onChange = {
            runOnUiThread {
                refreshTelegram()
                maybePrompt()
            }
        }
        if (Prefs.tgEnabled(this) && TelegramManager.state == TelegramManager.State.OFF) {
            startTgService()
        }
        refreshTelegram()
        maybePrompt()
    }

    override fun onPause() {
        TelegramManager.onChange = null
        super.onPause()
    }

    // ---------- واجهة المستخدم ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun label(text: String, sizeSp: Float, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = sizeSp
        if (bold) tv.setTypeface(tv.typeface, Typeface.BOLD)
        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.topMargin = dp(8)
        tv.layoutParams = lp
        return tv
    }

    private fun section(title: String): TextView {
        val tv = label(title, 18f, true)
        tv.setTextColor(Color.parseColor("#D32F2F"))
        (tv.layoutParams as LinearLayout.LayoutParams).topMargin = dp(24)
        return tv
    }

    private fun btn(text: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = text
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        val lp = LinearLayout.LayoutParams(match, wrap)
        lp.topMargin = dp(8)
        b.layoutParams = lp
        return b
    }

    private fun buildUi() {
        val root = ScrollView(this)
        root.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(16), dp(24), dp(16), dp(40))
        root.addView(col)

        col.addView(label("AlertMe — تنبيه الرسائل المهمة", 22f, true))
        col.addView(
            label(
                "بيراقب رسائل واتساب وتلجرام، ولو وصلت رسالة من شخص حددته هيطلعلك تنبيه بنغمة وصورة في نص الشاشة وزر موافق.",
                14f
            )
        )

        col.addView(section("الصلاحيات"))
        statusView = label("", 15f)
        col.addView(statusView)
        col.addView(btn("1) تفعيل قراءة الإشعارات (لواتساب)") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        col.addView(btn("2) السماح بالعرض فوق التطبيقات") {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        })
        col.addView(btn("3) إيقاف توفير البطارية للتطبيق") { requestBattery() })

        col.addView(section("تلجرام (اتصال مباشر — من غير إشعارات)"))
        tgStatus = label("", 15f)
        col.addView(tgStatus)
        tgLoginBtn = btn("تسجيل الدخول لتلجرام") { showTelegramLogin() }
        col.addView(tgLoginBtn)
        tgLogoutBtn = btn("تسجيل الخروج وإيقاف المراقبة") { confirmTelegramLogout() }
        col.addView(tgLogoutBtn)

        col.addView(section("شكل التنبيه"))
        previewImage = ImageView(this)
        previewImage.setBackgroundColor(Color.parseColor("#1E1E1E"))
        previewImage.scaleType = ImageView.ScaleType.FIT_CENTER
        val ilp = LinearLayout.LayoutParams(match, dp(180))
        ilp.topMargin = dp(8)
        previewImage.layoutParams = ilp
        col.addView(previewImage)
        col.addView(btn("اختيار الصورة") { pick("image/*", reqImage) })
        soundLabel = label("", 14f)
        col.addView(soundLabel)
        col.addView(btn("اختيار نغمة الرنين") { pick("audio/*", reqSound) })
        col.addView(btn("تجربة التنبيه") {
            val i = Intent(this, AlertActivity::class.java)
            i.putExtra(AlertActivity.EXTRA_HEADER, "رسالة من (تجربة)")
            i.putExtra(AlertActivity.EXTRA_SOURCE, "تلجرام")
            i.putExtra(AlertActivity.EXTRA_PREVIEW, "ده تنبيه تجريبي")
            startActivity(i)
        })
        col.addView(btn("مسح الصورة والنغمة المخصصة") {
            Prefs.imageFile(this).delete()
            Prefs.soundFile(this).delete()
            Prefs.setSoundName(this, null)
            refresh()
        })

        col.addView(section("الأشخاص المراقَبون"))
        col.addView(
            label(
                "حدد الشخص بالاسم أو @اليوزرنيم أو رقم الموبايل، وبعدها حدد المكان: أي مكان، أو الخاص فقط، أو جروب محدد.",
                13f
            )
        )
        col.addView(btn("+ إضافة شخص") { showAddDialog() })
        rulesContainer = LinearLayout(this)
        rulesContainer.orientation = LinearLayout.VERTICAL
        col.addView(rulesContainer, LinearLayout.LayoutParams(match, wrap))

        setContentView(root)
    }

    private fun refresh() {
        val ok = "✅ "
        val no = "❌ "
        statusView.text = listOf(
            (if (isListenerEnabled()) ok else no) + "قراءة الإشعارات (لواتساب)",
            (if (Settings.canDrawOverlays(this)) ok else no) + "العرض فوق التطبيقات",
            (if (isIgnoringBattery()) ok else no) + "استثناء من توفير البطارية"
        ).joinToString("\n")

        val bmp = ImageUtil.load(Prefs.imageFile(this), 600)
        if (bmp != null) {
            previewImage.setImageBitmap(bmp)
        } else {
            previewImage.setImageResource(R.drawable.ic_launcher_foreground)
        }

        val soundText = if (Prefs.soundFile(this).exists()) {
            Prefs.getSoundName(this) ?: "مخصصة"
        } else {
            "الافتراضية (نغمة المنبه)"
        }
        soundLabel.text = "النغمة: $soundText"

        renderRules()
    }

    private fun renderRules() {
        rulesContainer.removeAllViews()
        val rules = Prefs.getRules(this)
        if (rules.isEmpty()) {
            rulesContainer.addView(label("لسه مفيش أشخاص مضافين.", 14f))
            return
        }
        for ((index, r) in rules.withIndex()) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_VERTICAL
            row.setPadding(0, dp(6), 0, dp(6))

            val apps = listOfNotNull(
                if (r.whatsapp) "واتساب" else null,
                if (r.telegram) "تلجرام" else null
            ).joinToString(" + ")
            val where = when (r.scope) {
                Rule.SCOPE_PRIVATE -> "الخاص فقط"
                Rule.SCOPE_GROUP -> "جروب: ${r.group}"
                else -> "أي مكان"
            }

            val tv = TextView(this)
            tv.text = "${r.person}\n$where\n$apps"
            tv.textSize = 15f
            row.addView(tv, LinearLayout.LayoutParams(0, wrap, 1f))

            val del = Button(this)
            del.text = "حذف"
            del.setOnClickListener {
                val list = Prefs.getRules(this)
                if (index < list.size) {
                    list.removeAt(index)
                    Prefs.saveRules(this, list)
                }
                renderRules()
            }
            row.addView(del, LinearLayout.LayoutParams(wrap, wrap))

            rulesContainer.addView(row)
        }
    }

    private fun showAddDialog() {
        val pad = dp(16)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, 0)
        box.layoutDirection = View.LAYOUT_DIRECTION_RTL

        val hint = TextView(this)
        hint.text = "الشخص: اكتب اسمه، أو @اليوزرنيم، أو رقم موبايله (+20...). " +
                "اليوزرنيم والرقم بيشتغلوا مع تلجرام. في واتساب الرقم بيشتغل بس لو ظاهر في الإشعار (جهة اتصال غير محفوظة)."
        hint.textSize = 13f
        val person = EditText(this)
        person.hint = "اسم / @يوزرنيم / رقم"
        person.setSingleLine()

        val scopeTitle = TextView(this)
        scopeTitle.text = "المكان:"
        scopeTitle.textSize = 14f
        val rg = RadioGroup(this)
        rg.orientation = RadioGroup.VERTICAL
        val rbAny = RadioButton(this)
        rbAny.text = "أي مكان (الخاص وكل الجروبات)"
        rbAny.id = View.generateViewId()
        val rbPrivate = RadioButton(this)
        rbPrivate.text = "الخاص فقط"
        rbPrivate.id = View.generateViewId()
        val rbGroup = RadioButton(this)
        rbGroup.text = "جروب محدد فقط"
        rbGroup.id = View.generateViewId()
        rg.addView(rbAny)
        rg.addView(rbPrivate)
        rg.addView(rbGroup)
        rg.check(rbAny.id)

        val group = EditText(this)
        group.hint = "اسم الجروب بالظبط زي ما هو ظاهر"
        group.setSingleLine()
        group.visibility = View.GONE
        rg.setOnCheckedChangeListener { _, id ->
            group.visibility = if (id == rbGroup.id) View.VISIBLE else View.GONE
        }

        val wa = CheckBox(this)
        wa.text = "واتساب"
        wa.isChecked = true
        val tg = CheckBox(this)
        tg.text = "تلجرام"
        tg.isChecked = true

        box.addView(hint)
        box.addView(person)
        box.addView(scopeTitle)
        box.addView(rg)
        box.addView(group)
        box.addView(wa)
        box.addView(tg)

        val sv = ScrollView(this)
        sv.addView(box)

        AlertDialog.Builder(this)
            .setTitle("إضافة شخص")
            .setView(sv)
            .setPositiveButton("حفظ") { _, _ ->
                val name = person.text.toString().trim()
                val groupName = group.text.toString().trim()
                val scope = when (rg.checkedRadioButtonId) {
                    rbPrivate.id -> Rule.SCOPE_PRIVATE
                    rbGroup.id -> Rule.SCOPE_GROUP
                    else -> Rule.SCOPE_ANY
                }
                if (name.isEmpty()) {
                    toast("اكتب اسم الشخص أو يوزرنيمه أو رقمه")
                } else if (!wa.isChecked && !tg.isChecked) {
                    toast("اختار واتساب أو تلجرام")
                } else if (name.startsWith("@") && !tg.isChecked) {
                    toast("اليوزرنيم بيشتغل على تلجرام بس")
                } else if (scope == Rule.SCOPE_GROUP && groupName.isEmpty()) {
                    toast("اكتب اسم الجروب")
                } else {
                    val list = Prefs.getRules(this)
                    list.add(
                        Rule(
                            name,
                            if (scope == Rule.SCOPE_GROUP) groupName else "",
                            wa.isChecked,
                            tg.isChecked,
                            scope
                        )
                    )
                    Prefs.saveRules(this, list)
                    renderRules()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- تلجرام ----------

    private fun startTgService() {
        try {
            startForegroundService(Intent(this, TelegramService::class.java))
        } catch (e: Exception) {
            toast("تعذر تشغيل خدمة تلجرام")
        }
    }

    private fun refreshTelegram() {
        val st = TelegramManager.state
        val base = when (st) {
            TelegramManager.State.READY -> "✅ متصل — بيراقب رسايلك"
            TelegramManager.State.OFF ->
                if (Prefs.tgEnabled(this)) "⏳ جاري الاتصال..." else "❌ غير متصل"
            TelegramManager.State.CONNECTING -> "⏳ جاري الاتصال..."
            TelegramManager.State.NEED_PHONE,
            TelegramManager.State.NEED_CODE,
            TelegramManager.State.NEED_PASSWORD -> "⏳ في انتظار بيانات تسجيل الدخول"
            TelegramManager.State.UNSUPPORTED -> "⚠ خطوة تسجيل دخول غير مدعومة"
        }
        val err = TelegramManager.lastError
        tgStatus.text = if (err.isNotEmpty() && st != TelegramManager.State.READY) {
            "$base\n⚠ $err"
        } else {
            base
        }
        val ready = st == TelegramManager.State.READY
        tgLoginBtn.visibility = if (ready) View.GONE else View.VISIBLE
        tgLogoutBtn.visibility = if (ready) View.VISIBLE else View.GONE
    }

    private fun maybePrompt() {
        if (isFinishing || isDestroyed) return
        when (TelegramManager.state) {
            TelegramManager.State.NEED_PHONE -> if (phoneDialog == null) showPhoneDialog()
            TelegramManager.State.NEED_CODE -> if (codeDialog == null) showCodeDialog()
            TelegramManager.State.NEED_PASSWORD -> if (passDialog == null) showPasswordDialog()
            else -> {
            }
        }
    }

    private fun field(hint: String, type: Int): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = type
        return e
    }

    private fun dialogBox(message: String?, vararg fields: View): LinearLayout {
        val pad = dp(16)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(pad, pad, pad, 0)
        box.layoutDirection = View.LAYOUT_DIRECTION_RTL
        if (message != null) {
            val tv = TextView(this)
            tv.text = message
            tv.textSize = 14f
            box.addView(tv)
        }
        for (f in fields) box.addView(f)
        return box
    }

    private fun showTelegramLogin() {
        val st = TelegramManager.state
        if (st == TelegramManager.State.NEED_CODE || st == TelegramManager.State.NEED_PASSWORD) {
            maybePrompt()
            return
        }
        val apiId = field("API ID", InputType.TYPE_CLASS_NUMBER)
        val apiHash = field("API Hash", InputType.TYPE_CLASS_TEXT)
        val phone = field("رقم الموبايل بصيغة +20...", InputType.TYPE_CLASS_PHONE)
        if (Prefs.tgApiId(this) > 0) apiId.setText(Prefs.tgApiId(this).toString())
        apiHash.setText(Prefs.tgApiHash(this))

        val box = dialogBox(
            "هات API ID و API Hash من موقع my.telegram.org ← API development tools (مرة واحدة، وماتشاركهمش مع حد).",
            apiId, apiHash, phone
        )
        AlertDialog.Builder(this)
            .setTitle("تسجيل الدخول لتلجرام")
            .setView(box)
            .setPositiveButton("متابعة") { _, _ ->
                val id = apiId.text.toString().trim().toIntOrNull()
                val hash = apiHash.text.toString().trim()
                val ph = phone.text.toString().trim()
                if (id == null || id <= 0 || hash.isEmpty() || ph.isEmpty()) {
                    toast("كمّل كل الخانات")
                } else {
                    Prefs.setTgCredentials(this, id, hash)
                    Prefs.setTgEnabled(this, true)
                    TelegramManager.pendingPhone = ph
                    if (TelegramManager.hasClient()) {
                        TelegramManager.restart()
                    } else {
                        startTgService()
                    }
                    refreshTelegram()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun showPhoneDialog() {
        val phone = field("رقم الموبايل بصيغة +20...", InputType.TYPE_CLASS_PHONE)
        phoneDialog = AlertDialog.Builder(this)
            .setTitle("رقم الموبايل")
            .setView(dialogBox(null, phone))
            .setPositiveButton("تأكيد") { _, _ ->
                val ph = phone.text.toString().trim()
                if (ph.isNotEmpty()) TelegramManager.submitPhone(ph)
            }
            .setNegativeButton("إلغاء", null)
            .setOnDismissListener { phoneDialog = null }
            .create()
        phoneDialog?.show()
    }

    private fun showCodeDialog() {
        val code = field("كود التحقق", InputType.TYPE_CLASS_NUMBER)
        codeDialog = AlertDialog.Builder(this)
            .setTitle("كود التحقق")
            .setView(dialogBox("الكود اتبعت على تطبيق تلجرام عندك (أو SMS).", code))
            .setPositiveButton("تأكيد") { _, _ ->
                val c = code.text.toString().trim()
                if (c.isNotEmpty()) TelegramManager.submitCode(c)
            }
            .setNegativeButton("إلغاء", null)
            .setOnDismissListener { codeDialog = null }
            .create()
        codeDialog?.show()
    }

    private fun showPasswordDialog() {
        val pw = field(
            "كلمة سر التحقق بخطوتين",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        passDialog = AlertDialog.Builder(this)
            .setTitle("التحقق بخطوتين")
            .setView(dialogBox("حسابك عليه كلمة سر إضافية.", pw))
            .setPositiveButton("تأكيد") { _, _ ->
                val p = pw.text.toString()
                if (p.isNotEmpty()) TelegramManager.submitPassword(p)
            }
            .setNegativeButton("إلغاء", null)
            .setOnDismissListener { passDialog = null }
            .create()
        passDialog?.show()
    }

    private fun confirmTelegramLogout() {
        AlertDialog.Builder(this)
            .setTitle("تسجيل الخروج")
            .setMessage("هتتوقف مراقبة تلجرام وتتمسح الجلسة من التطبيق. متأكد؟")
            .setPositiveButton("خروج") { _, _ ->
                TelegramManager.logout(this)
                toast("تم تسجيل الخروج")
                refreshTelegram()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    // ---------- الصلاحيات ----------

    private fun isListenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == packageName
        }
    }

    private fun isIgnoringBattery(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestBattery() {
        try {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    // ---------- اختيار الصورة والنغمة ----------

    private fun pick(mime: String, req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
        i.addCategory(Intent.CATEGORY_OPENABLE)
        i.type = mime
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        when (requestCode) {
            reqImage -> {
                if (copyUri(uri, Prefs.imageFile(this))) toast("تم حفظ الصورة") else toast("تعذر قراءة الصورة")
            }
            reqSound -> {
                if (copyUri(uri, Prefs.soundFile(this))) {
                    Prefs.setSoundName(this, displayName(uri))
                    toast("تم حفظ النغمة")
                } else {
                    toast("تعذر قراءة النغمة")
                }
            }
        }
        refresh()
    }

    private fun copyUri(uri: Uri, dest: File): Boolean {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return false
            input.use { src ->
                dest.outputStream().use { out -> src.copyTo(out) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun displayName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}

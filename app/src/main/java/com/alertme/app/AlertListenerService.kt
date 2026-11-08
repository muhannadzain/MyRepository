package com.alertme.app

import android.app.Notification
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * بيقرأ إشعارات واتساب (وتلجرام لو الاتصال المباشر مش شغال)،
 * ولو الإشعار مطابق لأي قاعدة (شخص + مكان) بيطلّع التنبيه.
 */
class AlertListenerService : NotificationListenerService() {

    private val lastSeen = HashMap<String, String>()

    companion object {
        private val WHATSAPP = setOf("com.whatsapp", "com.whatsapp.w4b")
        private val TELEGRAM = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.thunderdog.challegram"
        )
    }

    override fun onListenerDisconnected() {
        try {
            NotificationListenerService.requestRebind(
                ComponentName(this, AlertListenerService::class.java)
            )
        } catch (e: Exception) {
            // مفيش حاجة نعملها
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return
        val isWa = pkg in WHATSAPP
        val isTg = pkg in TELEGRAM
        if (!isWa && !isTg) return
        // لو تلجرام متصل مباشرة، متكررش التنبيه من الإشعارات
        if (isTg && TelegramManager.isReady()) return

        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        val rules = Prefs.getRules(this)
        if (rules.isEmpty()) return

        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val convTitle = extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""

        // آخر رسالة (لو الإشعار بنمط المحادثة) عشان ما ننبهش على رسايل قديمة
        var lastSender = ""
        var lastText = ""
        var lastTime = 0L
        @Suppress("DEPRECATION")
        val msgs = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null && msgs.isNotEmpty()) {
            val last = msgs[msgs.size - 1]
            if (last is Bundle) {
                lastText = last.getCharSequence("text")?.toString() ?: ""
                lastSender = last.getCharSequence("sender")?.toString() ?: ""
                lastTime = last.getLong("time", 0L)
                if (lastSender.isEmpty() && Build.VERSION.SDK_INT >= 28) {
                    @Suppress("DEPRECATION")
                    val p = last.getParcelable<android.app.Person>("sender_person")
                    lastSender = p?.name?.toString() ?: ""
                }
            }
        }
        val hasMessage = lastText.isNotEmpty() || lastSender.isNotEmpty()

        // جروب ولا خاص؟ (الجروب بيبقى له عنوان محادثة)
        val isGroup = convTitle.isNotEmpty() ||
                extras.getBoolean(Notification.EXTRA_IS_GROUP_CONVERSATION, false)

        // في الجروبات الإشعار القديم بيبقى شكله "الاسم: الرسالة"
        val senderGuess = when {
            lastSender.isNotEmpty() -> lastSender
            isGroup && text.contains(": ") -> text.substringBefore(": ")
            else -> ""
        }

        val names = ArrayList<String>()
        if (isGroup) {
            if (senderGuess.isNotBlank()) names.add(senderGuess)
        } else {
            if (title.isNotBlank()) names.add(title)
            if (lastSender.isNotBlank() && lastSender != title) names.add(lastSender)
        }
        // لو جهة الاتصال مش محفوظة واتساب بيعرض الرقم بدل الاسم
        val phones = names.map { RuleMatcher.digitsOf(it) }.filter { it.length >= 7 }
        val chatTitle = if (isGroup) convTitle.ifEmpty { title } else title
        val info = MsgInfo(names, emptyList(), phones, chatTitle, isGroup)

        for (rule in rules) {
            if (rule.person.isBlank()) continue
            if (isWa && !rule.whatsapp) continue
            if (isTg && !rule.telegram) continue
            if (!RuleMatcher.matches(rule, info)) continue

            // منع التكرار لنفس الرسالة لو الإشعار اتحدّث
            val signature = if (hasMessage) {
                "$lastSender|$lastText|$lastTime"
            } else {
                "$title|$text|${sbn.postTime}"
            }
            val key = sbn.key ?: pkg
            if (lastSeen[key] == signature) return
            if (lastSeen.size > 200) lastSeen.clear()
            lastSeen[key] = signature

            val who = if (isGroup) senderGuess.ifEmpty { title } else title.ifEmpty { lastSender }
            val app = if (isWa) "واتساب" else "تلجرام"
            val source = if (isGroup && chatTitle.isNotEmpty()) "$app — $chatTitle" else app
            val preview = (if (hasMessage) lastText else text).take(200)

            AlertLauncher.show(this, "رسالة من $who", source, preview)
            return
        }
    }
}

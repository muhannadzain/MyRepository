package com.alertme.app

import android.content.Context
import android.content.Intent
import android.os.Build
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File

/**
 * اتصال مباشر بتلجرام عن طريق TDLib (المكتبة الرسمية) بحسابك الشخصي،
 * من غير الاعتماد على الإشعارات. بيقرأ الرسايل الجديدة ويطابقها مع القواعد.
 */
object TelegramManager {

    enum class State { OFF, CONNECTING, NEED_PHONE, NEED_CODE, NEED_PASSWORD, READY, UNSUPPORTED }

    /** رسايل أقدم من كده (مثلًا وصلت والموبايل كان مقفول) ما بننبهش عليها. */
    private const val MAX_AGE_SEC = 1800L

    @Volatile
    var state: State = State.OFF
        private set

    @Volatile
    var lastError: String = ""
        private set

    @Volatile
    var pendingPhone: String = ""

    /** حالة الاتصال بسيرفرات تلجرام وآخر وقت وصل فيه تحديث (للتشخيص). */
    @Volatile
    var connection: String = ""
        private set

    @Volatile
    var lastUpdateMs: Long = 0L
        private set

    @Volatile
    var onChange: (() -> Unit)? = null

    @Volatile
    private var client: Client? = null

    private lateinit var appCtx: Context

    fun isReady(): Boolean = state == State.READY

    fun hasClient(): Boolean = client != null

    @Synchronized
    fun start(ctx: Context) {
        appCtx = ctx.applicationContext
        if (client != null) return
        if (Prefs.tgApiId(appCtx) <= 0 || Prefs.tgApiHash(appCtx).isBlank()) {
            lastError = "ادخل API ID و API Hash"
            setState(State.OFF)
            return
        }
        try {
            Client.execute(TdApi.SetLogVerbosityLevel(1))
        } catch (e: Throwable) {
            // مش مهم
        }
        lastError = ""
        setState(State.CONNECTING)
        client = Client.create({ o -> handle(o) }, null, null)
    }

    /** إعادة تشغيل العميل (مثلًا بعد تغيير API ID). */
    fun restart() {
        val c = client
        if (c == null) {
            Thread { start(appCtx) }.start()
        } else {
            c.send(TdApi.Close()) { r -> checkError(r) }
        }
    }

    fun submitPhone(phone: String) {
        pendingPhone = ""
        lastError = ""
        setState(State.CONNECTING)
        client?.send(TdApi.SetAuthenticationPhoneNumber(phone, null)) { r -> authResult(r, State.NEED_PHONE) }
    }

    fun submitCode(code: String) {
        lastError = ""
        setState(State.CONNECTING)
        client?.send(TdApi.CheckAuthenticationCode(code)) { r -> authResult(r, State.NEED_CODE) }
    }

    fun submitPassword(password: String) {
        lastError = ""
        setState(State.CONNECTING)
        client?.send(TdApi.CheckAuthenticationPassword(password)) { r -> authResult(r, State.NEED_PASSWORD) }
    }

    fun logout(ctx: Context) {
        appCtx = ctx.applicationContext
        Prefs.setTgEnabled(appCtx, false)
        pendingPhone = ""
        val c = client
        if (c != null) {
            c.send(TdApi.LogOut()) { r -> checkError(r) }
        } else {
            File(appCtx.filesDir, "tdlib").deleteRecursively()
            File(appCtx.filesDir, "tdlib_files").deleteRecursively()
            setState(State.OFF)
            try {
                appCtx.stopService(Intent(appCtx, TelegramService::class.java))
            } catch (e: Exception) {
                // مفيش حاجة
            }
        }
    }

    // ---------- معالجة تحديثات TDLib ----------

    private fun handle(o: Any?) {
        try {
            lastUpdateMs = System.currentTimeMillis()
            when (o) {
                is TdApi.UpdateAuthorizationState -> onAuth(o.authorizationState)
                is TdApi.UpdateNewMessage -> onMessage(o.message)
                is TdApi.UpdateConnectionState -> {
                    connection = when (o.state) {
                        is TdApi.ConnectionStateReady -> "متصل بالسيرفر"
                        is TdApi.ConnectionStateWaitingForNetwork -> "بانتظار الشبكة"
                        is TdApi.ConnectionStateConnecting -> "جاري الاتصال بالسيرفر"
                        is TdApi.ConnectionStateConnectingToProxy -> "جاري الاتصال بالسيرفر"
                        is TdApi.ConnectionStateUpdating -> "جاري تحديث الرسائل"
                        else -> ""
                    }
                    notifyChange()
                }
            }
        } catch (e: Throwable) {
            // أي خطأ في رسالة واحدة ما يوقفش الباقي
        }
    }

    private fun onAuth(s: TdApi.AuthorizationState) {
        when (s) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> sendParams()
            is TdApi.AuthorizationStateWaitPhoneNumber -> {
                val phone = pendingPhone
                if (phone.isNotBlank()) {
                    submitPhone(phone)
                } else {
                    setState(State.NEED_PHONE)
                }
            }
            is TdApi.AuthorizationStateWaitCode -> setState(State.NEED_CODE)
            is TdApi.AuthorizationStateWaitPassword -> setState(State.NEED_PASSWORD)
            is TdApi.AuthorizationStateReady -> {
                lastError = ""
                setState(State.READY)
            }
            is TdApi.AuthorizationStateLoggingOut -> setState(State.CONNECTING)
            is TdApi.AuthorizationStateClosing -> setState(State.CONNECTING)
            is TdApi.AuthorizationStateClosed -> {
                client = null
                setState(State.OFF)
                if (Prefs.tgEnabled(appCtx)) {
                    Thread { start(appCtx) }.start()
                } else {
                    try {
                        appCtx.stopService(Intent(appCtx, TelegramService::class.java))
                    } catch (e: Exception) {
                        // مفيش حاجة
                    }
                }
            }
            else -> {
                lastError = "خطوة تسجيل دخول غير مدعومة (${s.javaClass.simpleName})"
                setState(State.UNSUPPORTED)
            }
        }
    }

    private fun sendParams() {
        val p = TdApi.SetTdlibParameters()
        p.useTestDc = false
        p.databaseDirectory = File(appCtx.filesDir, "tdlib").absolutePath
        p.filesDirectory = File(appCtx.filesDir, "tdlib_files").absolutePath
        p.useFileDatabase = false
        p.useChatInfoDatabase = true
        p.useMessageDatabase = false
        p.useSecretChats = false
        p.apiId = Prefs.tgApiId(appCtx)
        p.apiHash = Prefs.tgApiHash(appCtx)
        p.systemLanguageCode = "ar"
        p.deviceModel = Build.MODEL ?: "Android"
        p.systemVersion = Build.VERSION.RELEASE ?: ""
        p.applicationVersion = "1.0"
        client?.send(p) { r -> checkError(r) }
    }

    private fun onMessage(m: TdApi.Message) {
        if (m.isOutgoing) return
        val ageSec = System.currentTimeMillis() / 1000L - m.date.toLong()
        if (ageSec > MAX_AGE_SEC) return

        val hasRules = Prefs.getRules(appCtx).any { it.telegram && it.person.isNotBlank() }
        if (!hasRules) return

        val text = textOf(m.content)
        val sender = m.senderId

        client?.send(TdApi.GetChat(m.chatId)) { chatRes ->
            val chat = chatRes as? TdApi.Chat
            val chatTitle = chat?.title ?: ""
            val isPrivate = chat?.type is TdApi.ChatTypePrivate || chat?.type is TdApi.ChatTypeSecret
            val isGroup = !isPrivate
            if (sender is TdApi.MessageSenderUser) {
                client?.send(TdApi.GetUser(sender.userId)) { userRes ->
                    evaluate(userRes as? TdApi.User, chatTitle, isGroup, text, false)
                }
            } else {
                evaluate(null, chatTitle, isGroup, text, true)
            }
        }
    }

    private fun evaluate(u: TdApi.User?, chatTitle: String, isGroup: Boolean, text: String, senderIsChat: Boolean) {
        val names = ArrayList<String>()
        val usernames = ArrayList<String>()
        val phones = ArrayList<String>()
        var display = ""

        if (u != null) {
            display = "${u.firstName} ${u.lastName}".trim()
            if (display.isNotEmpty()) names.add(display)
            val un = u.usernames
            if (un != null) {
                val active = un.activeUsernames
                if (active != null) usernames.addAll(active.toList())
                val editable = un.editableUsername
                if (!editable.isNullOrEmpty()) usernames.add(editable)
            }
            val ph = RuleMatcher.digitsOf(u.phoneNumber ?: "")
            if (ph.isNotEmpty()) phones.add(ph)
            if (display.isEmpty() && usernames.isNotEmpty()) display = "@" + usernames[0]
        } else if (senderIsChat && chatTitle.isNotEmpty()) {
            names.add(chatTitle)
            display = chatTitle
        }

        val info = MsgInfo(names, usernames, phones, chatTitle, isGroup)
        for (rule in Prefs.getRules(appCtx)) {
            if (!rule.telegram || rule.person.isBlank()) continue
            if (!RuleMatcher.matches(rule, info)) continue

            val source = if (isGroup && chatTitle.isNotEmpty()) "تلجرام — $chatTitle" else "تلجرام"
            val who = if (display.isEmpty()) "شخص" else display
            AlertLauncher.show(appCtx, "رسالة من $who", source, text.take(200))
            return
        }
    }

    private fun textOf(c: TdApi.MessageContent?): String {
        return when (c) {
            is TdApi.MessageText -> c.text.text
            is TdApi.MessagePhoto -> "📷 صورة"
            is TdApi.MessageVideo -> "🎞 فيديو"
            is TdApi.MessageVoiceNote -> "🎤 رسالة صوتية"
            is TdApi.MessageDocument -> "📎 ملف"
            is TdApi.MessageSticker -> "ستيكر"
            else -> "رسالة جديدة"
        }
    }

    private fun checkError(r: Any?) {
        if (r is TdApi.Error) {
            lastError = r.message ?: "خطأ"
            notifyChange()
        }
    }

    private fun authResult(r: Any?, retry: State) {
        if (r is TdApi.Error) {
            lastError = r.message ?: "خطأ"
            setState(retry)
        }
    }

    private fun setState(s: State) {
        state = s
        notifyChange()
    }

    private fun notifyChange() {
        try {
            onChange?.invoke()
        } catch (e: Throwable) {
            // مفيش حاجة
        }
    }
}

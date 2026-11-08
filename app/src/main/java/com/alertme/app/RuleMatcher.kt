package com.alertme.app

/** معلومات الرسالة اللي بتتطابق مع القواعد (من واتساب أو تلجرام). */
data class MsgInfo(
    val names: List<String>,      // أسماء المرسل
    val usernames: List<String>,  // يوزرنيمات المرسل (تلجرام فقط)
    val phones: List<String>,     // أرقام المرسل (أرقام بس)
    val chatTitle: String,        // اسم الجروب (أو اسم الشخص في الخاص)
    val isGroup: Boolean          // الرسالة في جروب ولا في الخاص
)

object RuleMatcher {

    /** هل الرسالة دي تطابق القاعدة (الشخص + المكان)؟ */
    fun matches(rule: Rule, info: MsgInfo): Boolean {
        when (rule.scope) {
            Rule.SCOPE_PRIVATE -> if (info.isGroup) return false
            Rule.SCOPE_GROUP -> {
                if (!info.isGroup) return false
                if (!groupMatches(info.chatTitle, rule.group)) return false
            }
        }
        return personMatches(rule.person, info)
    }

    /** اسم الجروب لازم يتطابق بالظبط (مع تجاهل التشكيل/الهمزات/حالة الحروف). */
    fun groupMatches(chatTitle: String, groupRule: String): Boolean {
        val c = TextNorm.normalize(chatTitle)
        val g = TextNorm.normalize(groupRule)
        if (c.isEmpty() || g.isEmpty()) return false
        // واتساب بيضيف أحيانًا "(3 messages)" بعد اسم الجروب
        return c == g || c.startsWith("$g (")
    }

    /**
     * الشخص ممكن يتكتب كـ:
     *  - @username   (تلجرام فقط، مطابقة تامة)
     *  - رقم موبايل  (+20 100 123 4567 أو 0100...، مطابقة بآخر 9 أرقام)
     *  - اسم         (يحتوي على النص)
     */
    fun personMatches(personRaw: String, info: MsgInfo): Boolean {
        val p = personRaw.trim()
        if (p.isEmpty()) return false

        if (p.startsWith("@")) {
            val u = p.substring(1).trim().lowercase()
            if (u.isEmpty()) return false
            return info.usernames.any { it.trim().trimStart('@').lowercase() == u }
        }

        if (isPhone(p)) {
            val key = phoneKey(p) ?: return false
            return info.phones.any { digitsOf(it).contains(key) }
        }

        val n = TextNorm.normalize(p)
        if (n.isEmpty()) return false
        return info.names.any { TextNorm.normalize(it).contains(n) }
    }

    fun isPhone(s: String): Boolean {
        val t = s.trim()
        return t.all { it.isDigit() || it == ' ' || it == '+' || it == '-' || it == '(' || it == ')' } &&
                digitsOf(t).length >= 7
    }

    /** أرقام ASCII بس (بيحوّل الأرقام العربية كمان). */
    fun digitsOf(s: String): String {
        val sb = StringBuilder()
        for (ch in s) {
            val d = Character.digit(ch, 10)
            if (d >= 0) sb.append('0' + d)
        }
        return sb.toString()
    }

    private fun phoneKey(p: String): String? {
        var d = digitsOf(p).trimStart('0')
        if (d.length > 9) d = d.takeLast(9)
        return if (d.length >= 7) d else null
    }
}

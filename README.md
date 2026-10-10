# فتح — Fat7

لعبة استراتيجية عربية (ملف `Fat7.html`) تُحوَّل تلقائياً إلى تطبيق أندرويد عبر GitHub Actions.

## الحصول على الـ APK
1. ارفع محتويات هذا المجلد كما هي إلى مستودع GitHub (الفرع `main`).
2. افتح تبويب **Actions** وانتظر انتهاء «Build Fat7 APK» (بضع دقائق).
3. حمّل `Fat7.apk` من **Releases** (الوسم `latest`) أو من **Artifacts** داخل تشغيل الـ workflow.
4. ثبّته على الهاتف (اسمح بالتثبيت من مصدر غير معروف).

لإعادة البناء: Actions ← Build Fat7 APK ← Run workflow.

## الهيكل
- `Fat7.html` — اللعبة كاملة (ملف واحد). أي تعديل عليه يُبنى تلقائياً مع كل push.
- `android-res/` — الأيقونة وشاشة البداية والثيم (تُطبَّق بعد توليد مشروع أندرويد).
- `scripts/apply-android-branding.mjs` — يطبّق ما سبق ويضيف إذن الكاميرا.
- `capacitor.config.json` — اسم التطبيق `Fat7` والمعرّف `com.muhannad.fat7`.

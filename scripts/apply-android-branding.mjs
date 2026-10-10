// يُنفَّذ بعد `npx cap add android`: يضع الأيقونة وشاشة البداية والألوان ويضيف إذن الكاميرا (لمسح QR).
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const res = path.join(root, 'android/app/src/main/res');
const src = path.join(root, 'android-res');
if (!fs.existsSync(res)) throw new Error('android/ غير موجود — نفّذ npx cap add android أولاً');

// 1) احذف صور البداية الافتراضية لـ Capacitor (وإلا تتقدّم على شاشتنا في الوضع الأفقي/العمودي)
(function purge(dir) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) purge(p);
    else if (/^splash\.(png|jpg|webp)$/.test(e.name)) { fs.rmSync(p); console.log('removed', path.relative(root, p)); }
  }
})(res);

// 2) انسخ الموارد (أيقونات، splash، ثيم، ألوان)
fs.cpSync(src, res, { recursive: true, force: true });
console.log('copied android-res ->', path.relative(root, res));

// 3) AndroidManifest: إذن الكاميرا (اختياري، لمسح رمز QR)
const mf = path.join(root, 'android/app/src/main/AndroidManifest.xml');
let m = fs.readFileSync(mf, 'utf8');
if (!m.includes('android.permission.CAMERA')) {
  m = m.replace(/<\/manifest>\s*$/,
    '    <uses-permission android:name="android.permission.CAMERA" />\n' +
    '    <uses-feature android:name="android.hardware.camera" android:required="false" />\n' +
    '    <uses-feature android:name="android.hardware.camera.autofocus" android:required="false" />\n</manifest>\n');
  fs.writeFileSync(mf, m);
  console.log('manifest: camera permission added');
}

// 4) تأكد أن مكتبة core-splashscreen موجودة (يعتمد عليها ثيم Theme.SplashScreen)
const bg = path.join(root, 'android/app/build.gradle');
let g = fs.readFileSync(bg, 'utf8');
if (!g.includes('core-splashscreen')) {
  g = g.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.core:core-splashscreen:1.0.1"');
  fs.writeFileSync(bg, g);
  console.log('build.gradle: core-splashscreen added');
}
console.log('branding done');

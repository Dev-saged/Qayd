# SETUP — تجهيز الخدمات لقيد

## 1. أسرار GitHub (التوقيع)
Settings > Secrets and variables > Actions > New repository secret. أضف الأربعة كما في ملف `qayd-signing-secrets.txt` (عند ساجد، خارج المستودع):
`KEYSTORE_BASE64`، `SIGNING_KEY_ALIAS`، `SIGNING_STORE_PASSWORD`، `SIGNING_KEY_PASSWORD`.
بعدها كل رفع إلى main ينشر Release موقّعاً.

## 2. مشروع Firebase
1. console.firebase.google.com > **Create a project**.
   - الاسم: `Qayd` — عدّل معرّف المشروع إلى `qayd-ps` (إن كان محجوزاً: `qayd-ps-app`).
   - Google Analytics: **أوقفه** (غير مطلوب).
2. **Add app > Android**:
   - Package: `dev.saged.qayd` — الاسم: `قيد`.
   - SHA-1: `FF:34:93:B1:5C:4A:C9:2F:EA:56:4B:DA:AF:99:AC:B0:88:6C:48:73`
   - Register، ثم تخطَّ باقي الخطوات.
   - كرر **Add app > Android** بالحزمة `dev.saged.qayd.debug` (نسخة الاختبار) وبنفس SHA-1.
3. **Project settings > Your apps**: نزّل `google-services.json` (بعد إضافة التطبيقين، ليحتوي الاثنين).
   - أضف بصمة SHA-256 أيضاً: `B8:44:4B:33:A3:83:7C:BF:4E:59:24:A7:63:9E:8A:09:43:85:DE:D7:F2:19:7D:EF:57:AF:88:0F:42:EF:68:FB`
4. **Build > Authentication > Get started**:
   - Sign-in method > **Email/Password** > Enable (الخيار الأول فقط).
   - Settings > User actions > **ألغِ "Enable create (sign-up)"** حتى لا يُنشأ حساب إلا من لوحة المالك.
5. **Build > Firestore Database > Create database**:
   - Standard edition، الموقع **eur3 (Europe)** — لا يمكن تغييره لاحقاً.
   - Start in **production mode** (قواعد قيد تُنشر من المستودع).
6. **Authentication > Users > Add user** (حساب المالك):
   - البريد: `sajed@qayd.ps` وكلمة سر قوية تحتفظ بها لنفسك.
   - انسخ **User UID** وأرسله (هو الذي يأخذ صلاحية Super Admin).
7. **Project settings > Service accounts > Generate new private key**: ملف JSON لـ Worker. يُرفق في المحادثة فقط ويوضع سراً في Cloudflare؛ لا يدخل المستودع أبداً.

الخطة المجانية (Spark) تكفي للبداية: 50 ألف قراءة و20 ألف كتابة يومياً.

## 3. Cloudflare
- dash.cloudflare.com > My Profile > API Tokens > **Create Token** > القالب **Edit Cloudflare Workers** > Account: حسابك، Zone: All zones > Create.
- أرسل الرمز في المحادثة: يُستخدم من جلسة العمل لنشر Worker `qayd-admin` وتخزين مفتاح Firebase فيه كسر.

## ما يصل للمطوّر
- `google-services.json`
- User UID لحساب `sajed@qayd.ps`
- ملف مفتاح الخدمة (Service account JSON)
- رمز Cloudflare API

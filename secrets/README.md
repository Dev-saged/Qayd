# secrets — المستودع خاص

| الملف | الاستخدام |
|---|---|
| `signing/qayd-sajed-ps.jks` + `signing/keystore.properties` | توقيع APK (محلياً وفي GitHub Actions تلقائياً) |
| `signing/qayd-signing-secrets.txt` | نسخة نصية من بيانات التوقيع وبصمات SHA |
| `firebase-admin.json` | مفتاح خدمة Firebase (`FIREBASE_SA_PATH`) لسكربتات backend وسر Worker |
| `backend.env` | رمز Cloudflare، رقم الحساب، رابط Worker، مفتاح الويب، UID ساجد |

عند تغيير أي مفتاح: حدّث الملف هنا، ثم:
- Firebase: `npx wrangler secret put FIREBASE_SA < secrets/firebase-admin.json` داخل backend/worker.
- Cloudflare: يكفي تحديث `backend.env`.

لا تجعل المستودع عاماً وهذا المجلد فيه.

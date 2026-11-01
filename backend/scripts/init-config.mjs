// ينشئ config/features إن لم توجد (كل الوحدات مجانية افتراضياً).
// الاستخدام: FIREBASE_SA_PATH=... node backend/scripts/init-config.mjs
import { accessToken, call, loadSa } from './google.mjs';

const sa = loadSa();
const t = await accessToken(sa);
const url = `https://firestore.googleapis.com/v1/projects/${sa.project_id}/databases/(default)/documents/config/features`;
const fields = Object.fromEntries(['debts', 'ps', 'electricity', 'sms', 'pdf', 'reports'].map((k) => [k, { stringValue: 'free' }]));
try {
  await call(t, url);
  console.log('config/features exists');
} catch (e) {
  if (e.status !== 404) throw e;
  await call(t, url, 'PATCH', { fields });
  console.log('config/features created');
}

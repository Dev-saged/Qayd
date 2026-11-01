// يمنح صلاحية Super Admin لحساب واحد.
// الاستخدام: FIREBASE_SA_PATH=/path/sa.json node backend/scripts/set-super-admin.mjs <uid>
import { accessToken, call, loadSa } from './google.mjs';

const uid = process.argv[2];
if (!uid) throw new Error('usage: set-super-admin.mjs <uid>');
const sa = loadSa();
const token = await accessToken(sa);
const base = `https://identitytoolkit.googleapis.com/v1/projects/${sa.project_id}`;

const found = await call(token, `${base}/accounts:lookup`, 'POST', { localId: [uid] });
const user = found.users?.[0];
if (!user) throw new Error(`no user ${uid}`);
await call(token, `${base}/accounts:update`, 'POST', {
  localId: uid,
  customAttributes: JSON.stringify({ super_admin: true, role: 'super_admin' }),
});
console.log(`super_admin granted to ${user.email} (${uid})`);

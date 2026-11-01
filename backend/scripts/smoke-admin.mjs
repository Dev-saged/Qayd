// اختبار حي شامل: Worker + Auth + قواعد Firestore، ثم تنظيف كل ما أُنشئ.
// الاستخدام: FIREBASE_SA_PATH=... FIREBASE_WEB_KEY=... ADMIN_URL=... SUPER_UID=... node backend/scripts/smoke-admin.mjs
import { accessToken, call, customToken, loadSa } from './google.mjs';

const sa = loadSa();
const { FIREBASE_WEB_KEY: key, ADMIN_URL: admin, SUPER_UID: superUid } = process.env;
const p = sa.project_id;
const docs = `https://firestore.googleapis.com/v1/projects/${p}/databases/(default)/documents`;
const auth = `https://identitytoolkit.googleapis.com/v1`;
let failures = 0;
const check = (name, ok, extra = '') => { console.log(`${ok ? 'PASS' : 'FAIL'}  ${name} ${extra}`); if (!ok) failures++; };

async function idTokenFor(uid) {
  const r = await fetch(`${auth}/accounts:signInWithCustomToken?key=${key}`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ token: customToken(sa, uid), returnSecureToken: true }),
  });
  const j = await r.json();
  if (!j.idToken) throw new Error(JSON.stringify(j));
  return j.idToken;
}
async function signIn(email, password) {
  const r = await fetch(`${auth}/accounts:signInWithPassword?key=${key}`, {
    method: 'POST', headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ email, password, returnSecureToken: true }),
  });
  return r.json();
}
const post = (path, token, body) => fetch(`${admin}${path}`, {
  method: 'POST', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify(body),
}).then(async (r) => ({ status: r.status, body: await r.json() }));
const readAs = (token, path) => fetch(`${docs}/${path}`, { headers: { authorization: `Bearer ${token}` } }).then((r) => r.status);
const writeAs = (token, path, fields) => fetch(`${docs}/${path}`, {
  method: 'PATCH', headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' }, body: JSON.stringify({ fields }),
}).then((r) => r.status);

const tag = Math.random().toString(36).slice(2, 7);
const suffixA = `qa${tag}a.ps`;
const suffixB = `qa${tag}b.ps`;
const pass = `Qa-${tag}-pass!`;
const created = { users: [], shops: [], suffixes: [suffixA, suffixB] };

try {
  const superTok = await idTokenFor(superUid);
  check('unauthenticated rejected', (await post('/v1/shops', 'x.y.z', {})).status === 401);

  const a = await post('/v1/shops', superTok, { name: 'محل تجربة أ', suffix: suffixA, ownerUsername: 'owner', ownerPassword: pass, modules: { ps: true } });
  check('super creates shop A', a.status === 200, JSON.stringify(a.body));
  const b = await post('/v1/shops', superTok, { name: 'محل تجربة ب', suffix: suffixB, ownerUsername: 'owner', ownerPassword: pass });
  check('super creates shop B', b.status === 200);
  created.shops.push(a.body.shopId, b.body.shopId);
  created.users.push(a.body.ownerUid, b.body.ownerUid);
  check('duplicate suffix rejected', (await post('/v1/shops', superTok, { name: 'x', suffix: suffixA, ownerUsername: 'o2', ownerPassword: pass })).status === 409);

  const ownerA = await signIn(`owner@${suffixA}`, pass);
  check('owner signs in with password', !!ownerA.idToken);
  check('owner cannot create shops', (await post('/v1/shops', ownerA.idToken, { name: 'x', suffix: `z${suffixA}`, ownerUsername: 'o', ownerPassword: pass })).status === 403);

  const staff = await post('/v1/staff', ownerA.idToken, { username: 'ahmad', displayName: 'أحمد', password: pass });
  check('owner creates staff', staff.status === 200, JSON.stringify(staff.body));
  created.users.push(staff.body.uid);
  check('duplicate staff rejected', (await post('/v1/staff', ownerA.idToken, { username: 'ahmad', password: pass })).status === 409);

  const staffA = await signIn(`ahmad@${suffixA}`, pass);
  check('staff signs in', !!staffA.idToken);
  check('staff cannot create staff', (await post('/v1/staff', staffA.idToken, { username: 'x1', password: pass })).status === 403);

  const A = a.body.shopId;
  const B = b.body.shopId;
  check('owner reads own shop', (await readAs(ownerA.idToken, `shops/${A}`)) === 200);
  check('owner cannot read other shop', (await readAs(ownerA.idToken, `shops/${B}`)) === 403);
  check('staff reads own shop', (await readAs(staffA.idToken, `shops/${A}`)) === 200);
  check('staff writes shop data', (await writeAs(staffA.idToken, `shops/${A}/customers/c1`, { name: { stringValue: 'زبون' } })) === 200);
  check('staff cannot write other shop data', (await writeAs(staffA.idToken, `shops/${B}/customers/c1`, { name: { stringValue: 'x' } })) === 403);
  check('staff cannot edit members', (await writeAs(staffA.idToken, `shops/${A}/members/${staff.body.uid}`, { role: { stringValue: 'owner' } })) === 403);
  check('staff cannot rename shop', (await writeAs(staffA.idToken, `shops/${A}?updateMask.fieldPaths=name`, { name: { stringValue: 'x' } })) === 403);
  check('owner renames shop', (await writeAs(ownerA.idToken, `shops/${A}?updateMask.fieldPaths=name`, { name: { stringValue: 'محل أ — جديد' } })) === 200);
  check('owner cannot change suffix', (await writeAs(ownerA.idToken, `shops/${A}?updateMask.fieldPaths=suffix`, { suffix: { stringValue: 'x.ps' } })) === 403);
} finally {
  const t = await accessToken(sa);
  for (const uid of created.users.filter(Boolean)) {
    await call(t, `${auth}/projects/${p}/accounts:delete`, 'POST', { localId: uid }).catch(() => {});
  }
  const del = (path) => call(t, `${docs}/${path}`, 'DELETE').catch(() => {});
  for (const id of created.shops.filter(Boolean)) {
    const members = await call(t, `${docs}/shops/${id}/members`).catch(() => ({}));
    for (const d of members.documents || []) await call(t, `https://firestore.googleapis.com/v1/${d.name}`, 'DELETE').catch(() => {});
    await del(`shops/${id}/customers/c1`);
    await del(`shops/${id}`);
  }
  for (const s of created.suffixes) await del(`suffixes/${s}`);
  console.log('cleanup done');
}
if (failures) { console.log(`${failures} failed`); process.exit(1); }
console.log('all passed');

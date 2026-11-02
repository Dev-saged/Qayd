// qayd-admin: العمليات التي تحتاج صلاحيات Firebase Admin (إنشاء محل ومالكه، إنشاء موظف).
// كل طلب يحمل Firebase ID token؛ الصلاحية من claims: super_admin أو role=owner مع shopId.

const SECURETOKEN_JWKS = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const SUFFIX_RE = /^[a-z0-9-]{2,30}\.[a-z]{2,10}$/;
const USER_RE = /^[a-z0-9][a-z0-9._-]{1,31}$/;
const MODULES = ['debts', 'ps', 'electricity', 'sms', 'inventory', 'suppliers'];
const TYPES = ['general', 'grocery', 'clothing', 'food', 'household', 'games'];
const STAFF_PERMS = {
  quickSale: true, addDebt: true, addPayment: true, manageCustomers: true, deleteTx: false,
  viewBalances: true, expenses: false, managePs: true, setPrices: false, recordReadings: true,
  manageElectricity: false, sendSms: true, manageTemplates: false, exportPdf: true, viewReports: false,
  manageStock: false, manageSuppliers: false,
};

class Fail extends Error {
  constructor(status, code, message) { super(message); this.status = status; this.code = code; }
}

const json = (status, body) => new Response(JSON.stringify(body), {
  status, headers: { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store' },
});

const b64u = {
  enc: (buf) => btoa(String.fromCharCode(...new Uint8Array(buf))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''),
  encStr: (s) => b64u.enc(new TextEncoder().encode(s)),
  dec: (s) => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((s.length + 3) % 4)), (c) => c.charCodeAt(0)),
};

/* ---------- Google access token من مفتاح الخدمة ---------- */
let cachedToken = null;
async function accessToken(sa) {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp - 60 > now) return cachedToken.value;
  const pem = sa.private_key.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  const key = await crypto.subtle.importKey('pkcs8', b64u.dec(pem.replace(/\+/g, '-').replace(/\//g, '_')),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const unsigned = `${b64u.encStr(JSON.stringify({ alg: 'RS256', typ: 'JWT' }))}.${b64u.encStr(JSON.stringify({
    iss: sa.client_email, scope: 'https://www.googleapis.com/auth/cloud-platform',
    aud: sa.token_uri, iat: now, exp: now + 3600,
  }))}`;
  const sig = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  const r = await fetch(sa.token_uri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${unsigned}.${b64u.enc(sig)}` }),
  });
  const j = await r.json();
  if (!j.access_token) throw new Fail(502, 'google_auth', 'تعذر الاتصال بخدمات Google');
  cachedToken = { value: j.access_token, exp: now + (j.expires_in || 3600) };
  return cachedToken.value;
}

async function google(env, url, method = 'GET', body) {
  const token = await accessToken(env.sa);
  const r = await fetch(url, {
    method,
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  const data = text ? JSON.parse(text) : {};
  if (!r.ok) {
    const msg = data?.error?.message || '';
    if (msg.includes('EMAIL_EXISTS') || msg.includes('DUPLICATE_EMAIL')) throw new Fail(409, 'user_exists', 'اسم المستخدم مستخدم من قبل');
    if (msg.includes('WEAK_PASSWORD')) throw new Fail(400, 'weak_password', 'كلمة السر ضعيفة');
    if (r.status === 404) throw new Fail(404, 'not_found', 'غير موجود');
    if (r.status === 409 || msg.includes('ALREADY_EXISTS')) throw new Fail(409, 'conflict', 'موجود من قبل');
    throw new Fail(502, 'google', `خطأ من Firebase: ${msg || r.status}`);
  }
  return data;
}

/* ---------- التحقق من ID token ---------- */
let jwksCache = null;
async function jwks() {
  const now = Date.now();
  if (jwksCache && jwksCache.until > now) return jwksCache.keys;
  const r = await fetch(SECURETOKEN_JWKS);
  const maxAge = Number((r.headers.get('cache-control') || '').match(/max-age=(\d+)/)?.[1] || 3600);
  jwksCache = { keys: (await r.json()).keys, until: now + maxAge * 1000 };
  return jwksCache.keys;
}

async function verifyIdToken(env, token) {
  const parts = (token || '').split('.');
  if (parts.length !== 3) throw new Fail(401, 'unauthenticated', 'سجّل الدخول من جديد');
  let head;
  let claims;
  try {
    head = JSON.parse(new TextDecoder().decode(b64u.dec(parts[0])));
    claims = JSON.parse(new TextDecoder().decode(b64u.dec(parts[1])));
  } catch {
    throw new Fail(401, 'unauthenticated', 'سجّل الدخول من جديد');
  }
  const jwk = (await jwks()).find((k) => k.kid === head.kid);
  if (!jwk || head.alg !== 'RS256') throw new Fail(401, 'unauthenticated', 'سجّل الدخول من جديد');
  const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
  const ok = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, b64u.dec(parts[2]), new TextEncoder().encode(`${parts[0]}.${parts[1]}`));
  const now = Math.floor(Date.now() / 1000);
  if (!ok || claims.aud !== env.PROJECT_ID || claims.iss !== `https://securetoken.google.com/${env.PROJECT_ID}`
    || !claims.sub || claims.exp <= now || claims.iat > now + 300) {
    throw new Fail(401, 'unauthenticated', 'سجّل الدخول من جديد');
  }
  return claims;
}

/* ---------- Firestore REST ---------- */
function toValue(v) {
  if (v === null || v === undefined) return { nullValue: null };
  if (typeof v === 'boolean') return { booleanValue: v };
  if (typeof v === 'number') return Number.isInteger(v) ? { integerValue: String(v) } : { doubleValue: v };
  if (typeof v === 'string') return { stringValue: v };
  if (v instanceof Date) return { timestampValue: v.toISOString() };
  return { mapValue: { fields: toFields(v) } };
}
const toFields = (o) => Object.fromEntries(Object.entries(o).map(([k, v]) => [k, toValue(v)]));

function fromValue(v) {
  if ('stringValue' in v) return v.stringValue;
  if ('booleanValue' in v) return v.booleanValue;
  if ('integerValue' in v) return Number(v.integerValue);
  if ('doubleValue' in v) return v.doubleValue;
  if ('timestampValue' in v) return v.timestampValue;
  if ('mapValue' in v) return Object.fromEntries(Object.entries(v.mapValue.fields || {}).map(([k, x]) => [k, fromValue(x)]));
  return null;
}

const docsBase = (env) => `projects/${env.PROJECT_ID}/databases/(default)/documents`;
const fsUrl = (env, path) => `https://firestore.googleapis.com/v1/${docsBase(env)}${path}`;

async function getDoc(env, path) {
  try {
    const d = await google(env, fsUrl(env, `/${path}`));
    return Object.fromEntries(Object.entries(d.fields || {}).map(([k, v]) => [k, fromValue(v)]));
  } catch (e) {
    if (e.status === 404) return null;
    throw e;
  }
}

const write = (env, path, data, mustNotExist = false) => ({
  update: { name: `${docsBase(env)}/${path}`, fields: toFields(data) },
  updateTransforms: [
    { fieldPath: 'createdAt', setToServerValue: 'REQUEST_TIME' },
    { fieldPath: 'updatedAt', setToServerValue: 'REQUEST_TIME' },
  ],
  ...(mustNotExist ? { currentDocument: { exists: false } } : {}),
});

const commit = (env, writes) => google(env, fsUrl(env, ':commit'), 'POST', { writes });

/* ---------- Auth Admin ---------- */
const authBase = (env) => `https://identitytoolkit.googleapis.com/v1/projects/${env.PROJECT_ID}`;

async function createUser(env, email, password, displayName, claims) {
  const u = await google(env, `${authBase(env)}/accounts`, 'POST', { email, password, displayName, emailVerified: true });
  await google(env, `${authBase(env)}/accounts:update`, 'POST', { localId: u.localId, customAttributes: JSON.stringify(claims) });
  return u.localId;
}
const deleteUser = (env, uid) => google(env, `${authBase(env)}/accounts:delete`, 'POST', { localId: uid }).catch(() => {});

function randomId() {
  const a = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  return Array.from(crypto.getRandomValues(new Uint8Array(20)), (b) => a[b % a.length]).join('');
}

/* ---------- العمليات ---------- */
function clean(s, max = 60) { return String(s ?? '').trim().slice(0, max); }

function checkCreds(username, password) {
  if (!USER_RE.test(username)) throw new Fail(400, 'bad_username', 'اسم المستخدم: حروف إنجليزية صغيرة وأرقام فقط (2-32)');
  if (String(password || '').length < 8) throw new Fail(400, 'weak_password', 'كلمة السر 8 أحرف على الأقل');
}

async function createShop(env, caller, body) {
  if (caller.super_admin !== true) throw new Fail(403, 'forbidden', 'هذه العملية لمالك المنصة فقط');
  const name = clean(body.name);
  const suffix = clean(body.suffix, 40).toLowerCase();
  const ownerUsername = clean(body.ownerUsername, 32).toLowerCase();
  const ownerName = clean(body.ownerName) || name;
  if (!name) throw new Fail(400, 'bad_name', 'اكتب اسم المحل');
  if (!SUFFIX_RE.test(suffix)) throw new Fail(400, 'bad_suffix', 'نطاق المحل مثل anoar.ps');
  checkCreds(ownerUsername, body.ownerPassword);
  if (await getDoc(env, `suffixes/${suffix}`)) throw new Fail(409, 'suffix_taken', 'هذا النطاق محجوز لمحل آخر');

  const modules = Object.fromEntries(MODULES.map((m) => [m, body.modules?.[m] === true || (m === 'debts' && body.modules?.[m] !== false)]));
  const psScreens = Math.min(Math.max(parseInt(body.psScreens, 10) || 3, 1), 12);
  const shopId = randomId();
  const email = `${ownerUsername}@${suffix}`;
  const uid = await createUser(env, email, body.ownerPassword, ownerName, { shopId, role: 'owner' });
  try {
    await commit(env, [
      write(env, `suffixes/${suffix}`, { shopId }, true),
      write(env, `shops/${shopId}`, {
        name, suffix, active: true, licenseUntil: null, modules, psScreens, ownerUid: uid, currency: '₪',
        type: TYPES.includes(body.type) ? body.type : 'general',
      }),
      write(env, `shops/${shopId}/members/${uid}`, {
        username: email, displayName: ownerName, role: 'owner', perms: {}, active: true,
      }),
    ]);
  } catch (e) {
    await deleteUser(env, uid);
    throw e;
  }
  return { shopId, ownerUid: uid, ownerEmail: email };
}

async function createStaff(env, caller, body) {
  const isSuper = caller.super_admin === true;
  const shopId = isSuper ? clean(body.shopId, 40) : caller.shopId;
  if (!isSuper && caller.role !== 'owner') throw new Fail(403, 'forbidden', 'صاحب المحل فقط يضيف موظفين');
  const shop = shopId && await getDoc(env, `shops/${shopId}`);
  if (!shop) throw new Fail(404, 'no_shop', 'المحل غير موجود');
  if (shop.active !== true) throw new Fail(403, 'shop_inactive', 'اشتراك المحل متوقف');
  const username = clean(body.username, 32).toLowerCase();
  const displayName = clean(body.displayName) || username;
  checkCreds(username, body.password);
  const perms = Object.fromEntries(Object.keys(STAFF_PERMS).map((k) => [k, typeof body.perms?.[k] === 'boolean' ? body.perms[k] : STAFF_PERMS[k]]));
  const email = `${username}@${shop.suffix}`;
  const uid = await createUser(env, email, body.password, displayName, { shopId, role: 'staff' });
  try {
    await commit(env, [write(env, `shops/${shopId}/members/${uid}`, { username: email, displayName, role: 'staff', perms, active: true })]);
  } catch (e) {
    await deleteUser(env, uid);
    throw e;
  }
  return { uid, email };
}

export default {
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    try {
      if (request.method === 'GET' && pathname === '/v1/health') return json(200, { ok: true });
      if (request.method === 'GET' && pathname === '/v1/app/latest') {
        const meta = await env.QAYD_APP.get('meta', 'json');
        return json(meta ? 200 : 404, meta || { error: 'none' });
      }
      if (request.method === 'GET' && pathname === '/v1/app/apk') {
        const apk = await env.QAYD_APP.get('apk', 'arrayBuffer');
        if (!apk) return json(404, { error: 'none' });
        return new Response(apk, {
          headers: { 'content-type': 'application/vnd.android.package-archive', 'content-disposition': 'attachment; filename="Qayd.apk"' },
        });
      }
      if (request.method !== 'POST') throw new Fail(405, 'method', 'غير مسموح');
      if (!env.sa) env.sa = JSON.parse(env.FIREBASE_SA);
      const caller = await verifyIdToken(env, (request.headers.get('authorization') || '').replace(/^Bearer\s+/i, ''));
      const body = await request.json().catch(() => ({}));
      if (pathname === '/v1/shops') return json(200, await createShop(env, caller, body));
      if (pathname === '/v1/staff') return json(200, await createStaff(env, caller, body));
      throw new Fail(404, 'not_found', 'غير موجود');
    } catch (e) {
      if (e instanceof Fail) return json(e.status, { error: e.code, message: e.message });
      return json(500, { error: 'internal', message: 'خطأ غير متوقع، حاول مرة أخرى' });
    }
  },
};

// qayd-admin: العمليات التي تحتاج صلاحيات Firebase Admin (إنشاء محل ومالكه، إنشاء موظف).
// كل طلب يحمل Firebase ID token؛ الصلاحية من claims: super_admin أو role=owner مع shopId.

const SECURETOKEN_JWKS = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const SUFFIX_RE = /^[a-z0-9-]{2,30}\.[a-z]{2,10}$/;
const USER_RE = /^[a-z0-9][a-z0-9._-]{1,31}$/;
const MODULES = ['debts', 'ps', 'electricity', 'sms', 'inventory', 'suppliers', 'menu', 'layaway'];
const TYPES = ['general', 'grocery', 'clothing', 'food', 'household', 'games'];
const STAFF_PERMS = {
  quickSale: true, addDebt: true, addPayment: true, manageCustomers: true, deleteTx: false,
  viewBalances: true, expenses: false, managePs: true, setPrices: false, recordReadings: true,
  manageElectricity: false, sendSms: true, manageTemplates: false, exportPdf: true, viewReports: false,
  manageStock: false, manageSuppliers: false, manageMenu: false, confirmTransfers: false, discount: false,
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
  const wanted = clean(body.shopId, 40);
  const shopId = isSuper ? wanted : (wanted && (caller.shops || []).includes(wanted) ? wanted : caller.shopId);
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

/* ---------- النسخ الاحتياطية على الخادم وتنظيف القاعدة ---------- */
const DATA = ['customers', 'tx', 'recurring', 'readings', 'templates', 'settings', 'ps', 'audit', 'products', 'moves',
  'suppliers', 'stx', 'menu', 'orders', 'holds', 'usage', 'members'];
const KEEP_DAYS = 7;

async function rawGet(env, url) {
  const token = await accessToken(env.sa);
  const r = await fetch(url, { headers: { authorization: `Bearer ${token}` } });
  if (!r.ok) throw new Fail(502, 'google', `خطأ من Firebase: ${r.status}`);
  return r.text();
}

/** نسخة كاملة لمحل: نصوص Firestore كما هي دون تحليلها، فتبقى خفيفة على المعالج. */
async function backupShop(env, shopId, reason) {
  const parts = [`"shop":${await rawGet(env, fsUrl(env, `/shops/${shopId}`))}`];
  for (const c of DATA) {
    const pages = [];
    let token = '';
    do {
      const t = await rawGet(env, fsUrl(env, `/shops/${shopId}/${c}?pageSize=1000${token ? `&pageToken=${token}` : ''}`));
      pages.push(t);
      token = t.includes('"nextPageToken"') ? JSON.parse(t).nextPageToken : '';
    } while (token && pages.length < 20);
    parts.push(`"${c}":[${pages.join(',')}]`);
  }
  const at = new Date().toISOString();
  const body = `{"app":"qayd","format":"firestore-rest","shopId":"${shopId}","at":"${at}",${parts.join(',')}}`;
  const key = `bk:${shopId}:${at}`;
  await env.QAYD_APP.put(key, body, { expirationTtl: KEEP_DAYS * 86400, metadata: { at, size: body.length, reason } });
  return { key, at, size: body.length };
}

async function listShops(env) {
  const d = await google(env, fsUrl(env, '/shops?pageSize=300&mask.fieldPaths=name'));
  return (d.documents || []).map((x) => x.name.split('/').pop());
}

/** حذف الرسائل المنتهية بعد 30 يوماً، والسجلات المحذوفة حذفاً ناعماً بعد 60 يوماً. */
async function cleanShop(env, shopId) {
  const now = Date.now();
  const parent = `${docsBase(env)}/shops/${shopId}`;
  const query = (collectionId, field, values) => google(env, `https://firestore.googleapis.com/v1/${parent}:runQuery`, 'POST', {
    structuredQuery: {
      from: [{ collectionId }],
      where: values.length === 1
        ? { fieldFilter: { field: { fieldPath: field }, op: 'EQUAL', value: values[0] } }
        : { fieldFilter: { field: { fieldPath: field }, op: 'IN', value: { arrayValue: { values } } } },
      limit: 400,
    },
  });
  const old = (r, days, f = 'updatedAt') => (r || []).filter((x) => x.document)
    .filter((x) => { const t = x.document.fields?.[f]?.timestampValue || x.document.updateTime; return now - Date.parse(t) > days * 86400000; })
    .map((x) => ({ delete: x.document.name }));
  const writes = [
    ...old(await query('smsQueue', 'status', [{ stringValue: 'sent' }, { stringValue: 'delivered' }, { stringValue: 'failed' }]), 30, 'at'),
  ];
  for (const c of ['tx', 'customers', 'products', 'menu', 'suppliers', 'stx', 'templates', 'recurring', 'holds']) {
    writes.push(...old(await query(c, 'deleted', [{ booleanValue: true }]), 60));
  }
  for (let i = 0; i < writes.length; i += 450) await commit(env, writes.slice(i, i + 450));
  return writes.length;
}

/** كل 10 دقائق: نسخة لمحل واحد لم يُنسخ في هذه الفترة (3 فترات يومياً)، ثم تنظيفه مرة يومياً. */
async function scheduled(env) {
  if (!env.sa) env.sa = JSON.parse(env.FIREBASE_SA);
  const d = new Date();
  const slot = `${d.toISOString().slice(0, 10)}-${Math.floor(d.getUTCHours() / 8)}`;
  const day = d.toISOString().slice(0, 10);
  const st = (await env.QAYD_APP.get('bk:state', 'json')) || {};
  if (st.slot !== slot) { st.slot = slot; st.done = []; }
  if (st.day !== day) { st.day = day; st.cleaned = []; }
  const shops = await listShops(env);
  const next = shops.find((id) => !st.done.includes(id));
  if (next) {
    await backupShop(env, next, 'schedule');
    st.done.push(next);
  } else {
    const dirty = shops.find((id) => !st.cleaned.includes(id));
    if (dirty) { await cleanShop(env, dirty); st.cleaned.push(dirty); }
  }
  await env.QAYD_APP.put('bk:state', JSON.stringify(st));
}

async function listBackups(env, shopId) {
  const r = await env.QAYD_APP.list({ prefix: `bk:${shopId}:` });
  return r.keys.map((k) => ({ key: k.name, at: k.metadata?.at, size: k.metadata?.size, reason: k.metadata?.reason, expires: k.expiration }))
    .sort((a, b) => (a.at < b.at ? 1 : -1));
}

/** حذف المحل نهائياً بعد أن يفرّغ التطبيق بياناته: الحسابات، الأعضاء، النطاق، والمحل. */
async function removeShop(env, shopId) {
  const shop = await getDoc(env, `shops/${shopId}`);
  if (!shop) throw new Fail(404, 'no_shop', 'المحل غير موجود');
  const members = await google(env, fsUrl(env, `/shops/${shopId}/members?pageSize=300`));
  for (const m of members.documents || []) await deleteUser(env, m.name.split('/').pop());
  const writes = (members.documents || []).map((m) => ({ delete: m.name }));
  writes.push({ delete: `${docsBase(env)}/suffixes/${shop.suffix}` }, { delete: `${docsBase(env)}/presence/${shopId}` }, { delete: `${docsBase(env)}/shops/${shopId}` });
  await commit(env, writes);
  return { removed: shopId };
}

/** ربط صاحب محل بمحل إضافي: عضو مالك فيه، ويُضاف المحل لقائمة محلاته في صلاحياته. */
async function linkOwner(env, shopId, login, unlink) {
  const shop = await getDoc(env, `shops/${shopId}`);
  if (!shop) throw new Fail(404, 'no_shop', 'المحل غير موجود');
  const r = await google(env, `${authBase(env)}/accounts:lookup`, 'POST', { email: [clean(login, 80).toLowerCase()] });
  const u = r.users?.[0];
  if (!u) throw new Fail(404, 'no_user', 'لا يوجد حساب بهذا الاسم');
  const claims = JSON.parse(u.customAttributes || '{}');
  if (claims.role !== 'owner') throw new Fail(400, 'not_owner', 'الحساب ليس صاحب محل');
  if (claims.shopId === shopId) throw new Fail(400, 'primary', 'هذا محله الأساسي');
  const shops = new Set(claims.shops || []);
  if (unlink) shops.delete(shopId); else shops.add(shopId);
  await google(env, `${authBase(env)}/accounts:update`, 'POST', { localId: u.localId, customAttributes: JSON.stringify({ ...claims, shops: [...shops] }) });
  if (unlink) await commit(env, [{ delete: `${docsBase(env)}/shops/${shopId}/members/${u.localId}` }]);
  else await commit(env, [write(env, `shops/${shopId}/members/${u.localId}`, { username: u.email, displayName: u.displayName || u.email, role: 'owner', perms: {}, active: true })]);
  return { uid: u.localId, shops: [claims.shopId, ...shops] };
}

function requireSuper(caller) {
  if (caller.super_admin !== true) throw new Fail(403, 'forbidden', 'هذه العملية لمالك المنصة فقط');
}

/* ---------- صفحة كشف الحساب للزبون (رابط قصير) ---------- */
const esc = (t) => String(t ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const KIND = { debt: 'دين', payment: 'دفعة', discount: 'خصم', sale: 'بيع' };

async function statementPage(env, token) {
  if (!/^[A-Za-z0-9]{5,12}$/.test(token)) return null;
  const link = await getDoc(env, `links/${token}`);
  if (!link) return null;
  const [shop, cust] = await Promise.all([getDoc(env, `shops/${link.shopId}`), getDoc(env, `shops/${link.shopId}/customers/${link.customerId}`)]);
  if (!shop || !cust || cust.deleted === true || cust.link !== token) return null;
  const rows = await google(env, `https://firestore.googleapis.com/v1/${docsBase(env)}/shops/${link.shopId}:runQuery`, 'POST', {
    structuredQuery: { from: [{ collectionId: 'tx' }], where: { fieldFilter: { field: { fieldPath: 'customerId' }, op: 'EQUAL', value: { stringValue: link.customerId } } }, limit: 1000 },
  });
  const tx = rows.filter((r) => r.document).map((r) => Object.fromEntries(Object.entries(r.document.fields || {}).map(([k, v]) => [k, fromValue(v)])))
    .filter((t) => t.deleted !== true && KIND[t.kind]).sort((a, b) => (a.at < b.at ? 1 : -1));
  const bal = tx.reduce((s, t) => s + (t.kind === 'debt' ? t.amount : t.kind === 'sale' ? 0 : -t.amount), 0);
  const money = (v) => `${(Math.round(Math.abs(v) * 100) / 100).toLocaleString('en-US')} ₪`;
  const date = (iso) => { const d = new Date(iso); return `${d.getUTCDate()}/${d.getUTCMonth() + 1}/${d.getUTCFullYear()}`; };
  const list = tx.slice(0, 40).map((t) => `<li><span>${KIND[t.kind]}<small>${date(t.at)}</small></span><b class="${t.kind === 'debt' ? 'r' : 'g'}">${t.kind === 'debt' ? '+' : '−'}${money(t.amount)}</b></li>`).join('');
  return `<!doctype html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="robots" content="noindex">
<title>كشف حساب · ${esc(shop.name)}</title><style>
:root{--bg:#0E1322;--s:#171E31;--l:#263049;--t:#F3ECDC;--m:#9AA3B5;--g:#E4B24C;--r:#F08A80;--ok:#7DD3A6}
@media (prefers-color-scheme:light){:root{--bg:#F6F1E6;--s:#fff;--l:#E6DFCF;--t:#141826;--m:#5F6576;--g:#9C6B1C;--r:#B3261E;--ok:#1B7A4A}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--t);font:15px/1.6 system-ui,"Segoe UI",Tahoma,sans-serif}
main{max-width:520px;margin:auto;padding:22px 16px 40px}h1{font-size:20px;margin:0}p{margin:2px 0;color:var(--m)}
.card{background:var(--s);border:1px solid var(--l);border-radius:20px;padding:18px;margin-top:16px}
.bal{font-size:34px;font-weight:700;margin-top:4px}.r{color:var(--r)}.g{color:var(--ok)}
ul{list-style:none;margin:0;padding:0}li{display:flex;justify-content:space-between;align-items:center;padding:12px 0;border-bottom:1px solid var(--l)}li:last-child{border:0}
li span{display:flex;flex-direction:column}small{color:var(--m);font-size:12px}b{direction:ltr}
.rule{height:4px;border-top:2px solid var(--g);border-bottom:1px solid var(--g);margin:18px 0 8px;opacity:.8}footer{color:var(--m);font-size:12px;text-align:center}
</style></head><body><main>
<h1>${esc(shop.name)}</h1><p>كشف حساب: ${esc(cust.name)}</p>
<div class="card"><p>${bal > 0 ? 'الرصيد المستحق' : bal < 0 ? 'رصيد مقدّم لكم' : 'الحساب مسدّد'}</p><div class="bal ${bal > 0 ? 'r' : 'g'}">${money(bal)}</div></div>
<div class="card"><ul>${list || '<li><span>لا حركات</span></li>'}</ul></div>
<div class="rule"></div><footer>قيد · كل حق في قيده</footer></main></body></html>`;
}

export default {
  async scheduled(event, env, ctx) {
    ctx.waitUntil(scheduled(env).catch(() => {}));
  },
  async fetch(request, env) {
    const { pathname } = new URL(request.url);
    try {
      if (request.method === 'GET' && pathname === '/v1/health') return json(200, { ok: true });
      if (request.method === 'GET' && pathname.startsWith('/s/')) {
        if (!env.sa) env.sa = JSON.parse(env.FIREBASE_SA);
        const html = await statementPage(env, pathname.slice(3));
        return new Response(html || '<!doctype html><meta charset="utf-8"><p dir="rtl" style="font:16px system-ui;padding:24px">الرابط غير صالح أو أُلغي.</p>',
          { status: html ? 200 : 404, headers: { 'content-type': 'text/html; charset=utf-8', 'cache-control': 'no-store' } });
      }
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
      if (pathname === '/v1/backups/list') { requireSuper(caller); return json(200, { items: await listBackups(env, clean(body.shopId, 40)) }); }
      if (pathname === '/v1/backups/get') {
        requireSuper(caller);
        const key = clean(body.key, 120);
        if (!key.startsWith('bk:')) throw new Fail(400, 'bad_key', 'نسخة غير صالحة');
        const data = await env.QAYD_APP.get(key);
        if (!data) throw new Fail(404, 'not_found', 'النسخة غير موجودة أو انتهت مدتها');
        return new Response(data, { headers: { 'content-type': 'application/json; charset=utf-8' } });
      }
      if (pathname === '/v1/backups/run') { requireSuper(caller); return json(200, await backupShop(env, clean(body.shopId, 40), 'manual')); }
      if (pathname === '/v1/shops/link') { requireSuper(caller); return json(200, await linkOwner(env, clean(body.shopId, 40), body.login, body.unlink === true)); }
      if (pathname === '/v1/shops/remove') { requireSuper(caller); return json(200, await removeShop(env, clean(body.shopId, 40))); }
      throw new Fail(404, 'not_found', 'غير موجود');
    } catch (e) {
      if (e instanceof Fail) return json(e.status, { error: e.code, message: e.message });
      return json(500, { error: 'internal', message: 'خطأ غير متوقع، حاول مرة أخرى' });
    }
  },
};

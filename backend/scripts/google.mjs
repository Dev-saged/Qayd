// أدوات Google بمفتاح الخدمة (يُقرأ من FIREBASE_SA_PATH، ولا يدخل المستودع أبداً)
import crypto from 'node:crypto';
import fs from 'node:fs';

export function loadSa() {
  const p = process.env.FIREBASE_SA_PATH;
  if (!p) throw new Error('Set FIREBASE_SA_PATH to the service account JSON');
  return JSON.parse(fs.readFileSync(p, 'utf8'));
}

const b64u = (s) => Buffer.from(s).toString('base64url');

export async function accessToken(sa, scope = 'https://www.googleapis.com/auth/cloud-platform') {
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = b64u(JSON.stringify({ iss: sa.client_email, scope, aud: sa.token_uri, iat: now, exp: now + 3600 }));
  const sig = crypto.sign('RSA-SHA256', Buffer.from(`${head}.${claims}`), sa.private_key).toString('base64url');
  const r = await fetch(sa.token_uri, {
    method: 'POST',
    headers: { 'content-type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion: `${head}.${claims}.${sig}` }),
  });
  const j = await r.json();
  if (!j.access_token) throw new Error(`token: ${JSON.stringify(j)}`);
  return j.access_token;
}

export async function call(token, url, method = 'GET', body) {
  const r = await fetch(url, {
    method,
    headers: { authorization: `Bearer ${token}`, 'content-type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await r.text();
  const data = text ? JSON.parse(text) : {};
  if (!r.ok) throw Object.assign(new Error(`${method} ${url} → ${r.status}: ${text}`), { status: r.status, data });
  return data;
}

/** رمز مخصص لمستخدم (للاختبار): يُبدَّل بـ ID token عبر signInWithCustomToken. */
export function customToken(sa, uid) {
  const now = Math.floor(Date.now() / 1000);
  const head = b64u(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const claims = b64u(JSON.stringify({
    iss: sa.client_email, sub: sa.client_email,
    aud: 'https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit',
    iat: now, exp: now + 3600, uid,
  }));
  const sig = crypto.sign('RSA-SHA256', Buffer.from(`${head}.${claims}`), sa.private_key).toString('base64url');
  return `${head}.${claims}.${sig}`;
}

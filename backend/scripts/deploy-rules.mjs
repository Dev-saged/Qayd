// ينشر backend/firestore.rules على مشروع Firebase.
// الاستخدام: FIREBASE_SA_PATH=/path/sa.json node backend/scripts/deploy-rules.mjs
import fs from 'node:fs';
import { accessToken, call, loadSa } from './google.mjs';

const sa = loadSa();
const p = sa.project_id;
const token = await accessToken(sa);
const content = fs.readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');
const base = `https://firebaserules.googleapis.com/v1/projects/${p}`;

const ruleset = await call(token, `${base}/rulesets`, 'POST', { source: { files: [{ name: 'firestore.rules', content }] } });
const release = { name: `projects/${p}/releases/cloud.firestore`, rulesetName: ruleset.name };
try {
  await call(token, `${base}/releases/cloud.firestore`, 'PATCH', { release });
} catch (e) {
  if (e.status !== 404) throw e;
  await call(token, `${base}/releases`, 'POST', release);
}
console.log(`rules deployed: ${ruleset.name}`);

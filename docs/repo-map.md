# repo-map — قيد

## Layout
- `app/src/main/java/dev/saged/qayd/`
  - `MainActivity.kt` — splash, edge-to-edge, sets QaydTheme + QaydShell.
  - `config/BrandConfig.kt` — CONFIG: name, tagline, currency, PS warn minutes, OKLCH palette (light/dark).
  - `ui/theme/Theme.kt` — OKLCH→sRGB, Palette, Readex font, forced RTL.
  - `ui/icons/QIcons.kt` — hand-drawn 24×24 stroke icons.
  - `ui/components/Components.kt` — motion (Silk easing, entrance, pressScale, pulse, reduced-motion), Ring, Sparkline, Ambient background, text/icon helpers, 1s ticker.
  - `data/Models.kt` — Role, Module, SyncState, Account, Shop (blockReason), NewShop, QaydError, Validate.
  - `data/QaydRepo.kt` — Firebase Auth sign-in + cached claims (offline start), Firestore listeners (shop, shops) with sync state, rename/toggle shop, Worker calls (createShop, createStaff).
  - `ui/AppViewModel.kt` — Session (Loading/SignedOut/Active), Op state for network actions.
  - `ui/AppRoot.kt` — routing: Login → AdminScreen (super admin) or ShopRoot (QaydShell / Blocked).
  - `ui/auth/LoginScreen.kt`, `ui/admin/AdminScreen.kt` (shops list, create shop sheet, toggle active), `ui/more/MoreScreen.kt` (account, rename shop, add staff, sign out).
  - `ui/components/Forms.kt` (QField, QButton, QSwitchRow, QStepper, QNotice), `Chrome.kt` (SyncChip, IconTile, ActionRow, QSheet).
  - `ui/shell/QaydShell.kt` — tabs, bottom bar with center FAB; takes shop/account/sync/summary/slots + More slot.
  - `ui/home/HomeModels.kt` — UI models, HomeSummary.Empty, idleSlots, SampleHome (tests/preview).
  - `ui/home/HomeScreen.kt` — header + SyncChip, debt hero, PsGrid (module ps, N screens), module-aware KPIs, recent activity / empty state.
- `app/google-services.json` — Firebase client config (public by design; security is in rules).
- `backend/firestore.rules` — shop isolation by claims, owner-only rename, members guarded, module data requires active shop.
- `backend/worker/` — `qayd-admin` Worker (https://qayd-admin.dev-mr07.workers.dev): POST /v1/shops (super), POST /v1/staff (owner/super). Secret FIREBASE_SA.
- `backend/scripts/` — `deploy-rules.mjs`, `set-super-admin.mjs`, `smoke-admin.mjs` (live end-to-end test + cleanup). Need FIREBASE_SA_PATH.
- `app/src/test/.../ShellSnapshotTest.kt` — snapshots (home, home-new, login, admin, more) to `app/build/snapshots`, contrast + validation asserts.

## Data model (Firestore)
- `shops/{id}`: name, suffix, active, licenseUntil, modules{debts,ps,electricity,sms}, psScreens, ownerUid, currency, createdAt, updatedAt.
- `shops/{id}/members/{uid}`: username, displayName, role(owner|staff), perms{}, active.
- `suffixes/{suffix}`: shopId (uniqueness). Claims: super_admin | shopId + role.
- `app/src/main/res/` — adaptive icon (vector ق mark), splash theme, Readex fonts.
- `brand/` — master mark SVG + 1024 PNG.
- `docs/SPEC.md` — requirements, decisions, permissions. `docs/screens/` — current snapshots.
- `prototype/home-directions.html` — the three static directions (A approved).

## Local build
- `ROBOLECTRIC_DEPS_DIR` (optional): folder holding android-all-instrumented jar for offline Robolectric.

## Signing & releases
- Release key alias `sajed-qayd-ps` (CN=Sajed Al-Abadla, O=Qayd PS, C=PS). Keystore never in repo; CI reads it from secrets, locally from gitignored keystore.properties.
- CI publishes a Release per main build: tag vX.Y.Z-bN, notes = docs/releases/vX.Y.Z.md + commits since previous tag.

## Changes
- v0.1.0: shell + home (direction A), icon, CI, per-build releases.
- v0.2.0: Firebase auth + offline session, super admin panel, staff creation, shop rename, sync chip, Worker + rules.

## Local build notes
- Maven Central rate-limits this sandbox: ~/.gradle/init.d/central-mirror.gradle routes to Google's Central mirror (local only).

## Solved bugs
- Signed amounts flipped ("300+") inside RTL text → wrap in LTR isolate (`ltr()` in HomeScreen).
- Light theme: dark text on gold FAB/avatar failed contrast → BrandConfig onPri light tone.

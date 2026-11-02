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
  - `ui/components/Forms.kt` (QField, QButton, QSwitchRow, QStepper, QNotice), `Chrome.kt` (SyncChip, IconTile, ActionRow, QSheet), `Numpad.kt` (NumpadBody/NumpadSheet: in-app keypad, never the system keyboard).
  - `data/Ledger.kt` — TxKind, ExpenseCategory, Customer, Tx, Recurring, Ledger.of (derived balances, late, today sales/collected, month expenses, recurring paid status, 14-day debt trend), AmountInput.
  - `ui/customers/` (CustomersScreen list/search/sort, CustomerPage balance + history), `ui/expenses/ExpensesPage.kt` (month by category, recurring monthly payments), `ui/shell/ShellSheets.kt` (ShopActions, PadRequest, new-action sheet, customer picker, delete confirm), `ui/util/Format.kt` (money, cur, ltr, dates, norm).
  - `ui/shell/QaydShell.kt` — tabs + pages (customer, expenses), numpad host, FAB action sheet; takes shop/account/sync/ledger/slots/actions + More slot.
  - `ui/home/HomeModels.kt` — PsSlot, idleSlots, SampleHome.slots.
  - `ui/home/HomeScreen.kt` — header + SyncChip, debt hero from Ledger, QuickSale card (today's sales, "سجّل بيع"), PsGrid, KPIs.
- `app/google-services.json` — Firebase client config (public by design; security is in rules).
- `backend/firestore.rules` — shop isolation by claims, owner-only rename, members guarded, module data requires active shop.
- `backend/worker/` — `qayd-admin` Worker (https://qayd-admin.dev-mr07.workers.dev): POST /v1/shops (super), POST /v1/staff (owner/super). Secret FIREBASE_SA.
- `backend/scripts/` — `deploy-rules.mjs`, `set-super-admin.mjs`, `smoke-admin.mjs` (live end-to-end test + cleanup). Need FIREBASE_SA_PATH.
- `app/src/test/.../ShellSnapshotTest.kt` — snapshots (home, home-new, login, admin, more) to `app/build/snapshots`, contrast + validation asserts.

## Data model (Firestore)
- `shops/{id}`: name, suffix, active, licenseUntil, modules{debts,ps,electricity,sms}, psScreens, ownerUid, currency, createdAt, updatedAt.
- `shops/{id}/members/{uid}`: username, displayName, role(owner|staff), perms{}, active.
- `suffixes/{suffix}`: shopId (uniqueness). Claims: super_admin | shopId + role.
- `shops/{id}/customers/{cid}`: name, phone, note, by, byName, deleted, createdAt, updatedAt.
- `shops/{id}/tx/{tid}`: kind(sale|debt|payment|expense), amount, at, customerId?, category?, recurringId?, period?(YYYY-MM), by, byName, deleted.
- `shops/{id}/recurring/{rid}`: title, category, day, deleted.
- `app/src/main/res/` — adaptive icon (vector ق mark), splash theme, Readex fonts.
- `brand/` — master mark SVG + 1024 PNG; `brand/tools/make_kufi_icon.py` regenerates the Kufi icon layers; `brand/concepts/` holds the reviewed directions.
- `docs/SPEC.md` — requirements, decisions, permissions. `docs/screens/` — current snapshots.
- `prototype/home-directions.html` — the three static directions (A approved).

## Local build
- `ROBOLECTRIC_DEPS_DIR` (optional): folder holding android-all-instrumented jar for offline Robolectric.

## Signing & releases
- Release key alias `sajed-qayd-ps` (CN=Sajed Al-Abadla, O=Qayd PS, C=PS) stored in `secrets/signing` (private repo); CI and local builds sign from it automatically.
- CI publishes a Release per main build: tag vX.Y.Z-bN, notes = docs/releases/vX.Y.Z.md + commits since previous tag.

## Changes
- v0.1.0: shell + home (direction A), icon, CI, per-build releases.
- v0.2.0: Firebase auth + offline session, super admin panel, staff creation, shop rename, sync chip, Worker + rules.
- v1.10.0: Tx.wallet/payer/payerPhone/verify, TxKind.Discount, settings/payments wallets, closes collection + Ledger.cashToday, ui/money (TransfersPage, PayerSheet, CashClosePage), Report byWallet/monthDiscounts/topCustomers, Perm confirmTransfers/discount.
- v1.9.0: SmsSettings (settings/sms), StatementLinks + links/{token} + Worker GET /s/{token} HTML, Customer.limit/waPrefix/link, PadRequest.Limit + over-limit sheet, notify/SmsSchedule (exact alarms pre/run on designated device, WorkManager GatewayCheck), gateway/request flow, openWhatsApp, QaydRepo.get singleton.
- v1.8.0: ui/components/Avatars (12 drawn looks, members.avatar), QSwitchGroup + QSheet footer, system/SavedLogins (Keystore AES-GCM), DevFooter on login, Worker cron */10 (backup one shop per run into KV bk:{shop}:{iso} ttl 7d, daily cleanup), /v1/backups/list|get|run, /v1/shops/remove; repo wipe/delete/restore as super; rules super recursive on shops.
- v1.7.0: data/Platform (Plan, Presence, Payment, Broadcast), config/plans + config/broadcast, presence/{shopId} (written by shop app), payments (super only), Admin: plans/payment/broadcast sheets, renewals card, support mode (ShopRoot read-only with onExit), home notice banner.
- v1.6.0: Product.sizes/colors → variants, Move.variant, ProductStock.byVariant, returnProduct (negative sale + return move), data/Holds + ui/holds (deposits, pays array, deliver moves stock), Module.Layaway, rules holds.
- v1.5.0: data/Orders (menu, orders with daily no, sale tx in same batch), ui/orders (OrdersPage, NewOrder grid, MenuView, PrintSheet), system/BtPrinter (ESC/POS raster over SPP, 58/80mm), Module.Menu + Perm manageMenu, rules menu/orders.
- v1.4.0: data/Business (BizType presets + LocalQuickAdds), data/Stock (products + moves, derived qty), data/Suppliers (suppliers + stx), ui/stock (StockPage, scanner via zxing-android-embedded), ui/suppliers, Perm manageStock/manageSuppliers, rules products/moves/suppliers/stx, Worker MODULES+type.
- v1.3.1: PermissionsScreen drawn as overlay in AppRoot (keeps navigation state, BackHandler), status-bar scrim (windowInsetsTopHeight statusBars).
- v1.3.0: system/Updates (app-wide check/download state, release notes from assets/releases copied from docs/releases by gradle task releaseNotes), ui/updates (UpdateLayer sheet + download pill, UpdatesPage history), PaidSheet + ActionRow(locked, badge), MoreNav.reportsLocked.
- v1.2.0: system/Perms + ui/perms/PermissionsScreen (first run + More, RestrictedSmsSheet), Updater installs via PackageInstaller session + system/InstallReceiver, system/QaydFiles (Downloads/Qayd/Backups|Invoices), pdf/QaydPdf redesigned (Reem Kufi in assets/fonts, drawn icons, no author) with bitmap preview for tests.
- v1.1.0: Kufi icon (brand/tools/make_kufi_icon.py → launcher layers + drawable/qayd_logo), system/ThemePref (System/Light/Dark), system/AutoBackup (daily, Downloads/Qayd, keep 7; feature autoBackup), Shop.smsQuota + shops/{id}/usage/sms-YYYY-M counter enforced in rules, MessagesScreen bulk segments, ui/more/DevCard.
- v1.0.0: data/Reports.kt + ui/reports, system/AppLock (biometric, FragmentActivity), system/Backup (JSON share), system/Updater (Worker /v1/app/latest + /v1/app/apk from KV QAYD_APP, uploaded by CI), UpdateBanner + LockScreen in AppRoot.
- v0.9.0: FEATURES + Shop.paid/unlocked/featureOn/daysLeft, config/features (free|paid), AdminScreen EditShopSheet + features sheet, LockedCard (WhatsApp owner), shell filters locked modules.
- v0.8.0: pdf/QaydPdf.kt (PdfDocument + StaticLayout RTL, statement A4 paged, receipt 80mm, zxing QR, FileProvider share).
- v0.7.0: data/Sms.kt (SmsMsg, Gateway, Templates.fill), gateway/GatewayService (FGS specialUse: claims queued msgs by transaction, sends via SmsManager, heartbeat gateway/main), ui/messages (MessagesScreen, SendSheet, TemplatesPage, smsVars).
- v0.6.0: data/Power.kt (Reading, Subscriber, PowerState.bill), customers.power/meter/initialReading, shops/{id}/readings + debt tx source=power; ui/power/PowerPage (round mode, numpad preview/unit).
- v0.5.0: PsSlot (startedAt/paused/rate) synced via shops/{id}/ps/{n}; ui/ps sheets; notify/PsAlarms (exact alarms + receiver); settings/prices (psRate, kwhPrice) + PricesSheet.
- v0.4.0: Perm/Member/Access (data/Team.kt), ui/team (TeamPage perms + shift, AuditPage), rules enforce perms per collection + featureOn(config/features).
- v0.3.0: quick sale + in-app numpad, customers/debts/customer page, expenses + recurring monthly payments, seal icon, keys in private repo.

## Local build notes
- Maven Central rate-limits this sandbox: ~/.gradle/init.d/central-mirror.gradle routes to Google's Central mirror (local only).

## Solved bugs
- Signed amounts flipped ("300+") inside RTL text → wrap in LTR isolate (`ltr()` in HomeScreen).
- Light theme: dark text on gold FAB/avatar failed contrast → BrandConfig onPri light tone.

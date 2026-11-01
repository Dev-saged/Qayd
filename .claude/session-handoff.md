# session-handoff

## State
- v0.2.0 (phase 1 done): Firebase login with offline session, super admin panel (create/toggle shops), owner adds staff + renames shop, sync chip, module-aware home.
- Backend live: Firestore rules deployed; Worker qayd-admin at https://qayd-admin.dev-mr07.workers.dev (secret FIREBASE_SA); smoke test 19/19 passing.
- sajed@qayd.ps (uid hDdMcmxI3NPdSbWz55EkqN0PSI83) has super_admin claim.

## Decisions
- Name قيد / Qayd, package dev.saged.qayd, Firebase project qayd-ps (eur3), one project for all shops.
- Login = username@suffix as Firebase email; client sign-up disabled; accounts only via Worker.
- google-services.json committed (public config); service account + CF token never in repo.
- Super Admin panel lives inside the app. Every main build → signed Release vX.Y.Z-bN with Arabic notes.

## Open
- Signing secrets must be added by Sajed in GitHub (no Release is published without them).
- Home debt numbers/activity are empty until phase 2.

## Next step
- Phase 2 (v0.3.0): customers, customer page, custom numpad, debt/payment/advance, derived balance, search, statement.

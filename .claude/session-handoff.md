# session-handoff

## State
- v0.1.0: Kotlin/Compose shell with approved direction A home screen (sample data), ق mark icon, CI APK build.
- Debug + release APK build locally; snapshot + contrast tests pass.

## Decisions
- Name قيد / Qayd, package dev.saged.qayd, repo Dev-saged/Qayd.
- One Firebase project for all shops; usernames name@shopsuffix (e.g. ahmad@anoar.ps), suffix set by Super Admin.
- SMS gateway merged into this app; all needed permissions declared (docs/SPEC.md §14).
- Electricity amount = (current − previous) × price per kWh, proportional, price frozen per bill.
- Balance derived from transactions (positive = owes, negative = credit).

## Open
- Firebase project + google-services.json from Sajed.
- Signing secrets in GitHub (KEYSTORE_BASE64, SIGNING_STORE_PASSWORD, SIGNING_KEY_ALIAS, SIGNING_KEY_PASSWORD).

## Next step
- Login screen + Firebase Auth (username@suffix), shop/staff model, then customers + transactions with offline Firestore.

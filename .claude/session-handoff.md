# session-handoff

## State
- v0.3.0: quick sale (in-app numpad), customers + debts + customer page, expenses + recurring monthly payments, seal icon.
- v0.2.0 base: Firebase login/offline, super admin panel, staff creation, shop rename, sync chip.
- Backend live: rules + Worker qayd-admin; smoke test passes. Keys in secrets/ (private repo); CI signs and releases every main build.

## Decisions
- Name قيد / Qayd, package dev.saged.qayd, Firebase qayd-ps, one project for all shops.
- Amounts only via in-app numpad (never the phone keyboard); names/search use the system keyboard.
- Balance derived from tx (debt − payment); negative = credit for the customer.
- Expenses UI is owner-only; Firestore rules still allow any shop member (tighten in v0.4.0 with perms).

## Open
- Staff permission matrix not enforced yet (rules + UI) — v0.4.0.
- Customer statement / PDF — v0.8.0. PlayStation sessions still local-only — v0.5.0.

## Next step
- v0.4.0: team & permissions (matrix, enforce in rules), audit log, shifts.

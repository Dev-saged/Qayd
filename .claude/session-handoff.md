# session-handoff

## State
- v1.0.0 released: all roadmap phases done (shell, auth/offline, quick sale + numpad, customers/debts, expenses + recurring, team/perms/audit, PlayStation sync + alarms, electricity rounds, SMS gateway + templates, PDF statements/receipts, owner panel + paid features, reports, biometric lock, backup, self-update).
- Backend: rules enforce perms/features/isolation; Worker qayd-admin (shops, staff, app updates from KV). smoke-admin.mjs 31/31 passing.
- Keys in secrets/ (private repo). CI signs, releases, and publishes the APK to KV on every main build.

## Decisions
- v1.3.0: update sheet + background download pill + Updates page with in-app release history; paid features open PaidSheet.
- v1.2.0: permissions onboarding, session-based self-update (avoids Android restricted settings), Qayd folder for backups/invoices, premium PDF.
- v1.1.0: Kufi icon (B2), theme mode, auto backup (paid-able), SMS monthly quota per shop (-1 unlimited), bulk segments, dev card.
- SMS: Firestore queue + in-app gateway service (not LAN HTTP).
- Self-update via Worker KV because the repo is private.
- Paid features: config/features free|paid + shops.paid; enforced in rules for ps/electricity/sms.

## Open (after 1.0)
- Meter OCR, PS screen transfer, iOS. Device testing by Sajed.

## Next step
- Fix issues Sajed reports from real-device testing.

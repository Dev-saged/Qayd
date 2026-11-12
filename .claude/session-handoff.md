# session-handoff

## Resume
- اقرأ هذا الملف ثم docs/repo-map.md (فيه Quick index: ميزة → ملف). لا تفحص المستودع ولا تقرأ الملفات الكبيرة كاملة.

## State
- v1.13.0 on main: offline login vault (every signed-in account kept with its shop cache). v1.12.0 before that. Since 1.8: statement links, credit limits, monthly scheduled reminder on designated gateway device, WhatsApp 970/972 (1.9); payment methods + transfer review, discounts, top customers, cash close (1.10); offline voice entry with review inbox, home widget, contacts import (1.11); multi-shop owners with switcher (1.12).
- Worker: cron backups (KV ttl 7d) + cleanup, /s/{token} statement page, /v1/shops/link. Rules deployed each phase.
- Sajed tests on device; he asked for no automated tests in these phases (only build checks).

## Decisions
- Reminder sends only from the designated gateway device (settings/sms.gatewayDevice); staff request via gateway/request + WorkManager check.
- Voice: record PCM -> WAV kept; recognition from PCM (API 33+, on-device when available); never auto-saves.
- Transfers: Tx.wallet + verify pending/ok/missing; missing excluded from income.
- Rejected by Sajed: per-entry auto SMS, due dates/installments, cheques, multi-currency (shekel only). Deferred: meter OCR.

## Open
- Offline login: first sign-in per account needs internet; old sessions (pre-1.13) need one online sign-in to store the password check.
- Never tried on hardware: Bluetooth printer, voice recognition, statement page, multi-shop link flow.
- Key rotation (Firebase + Cloudflare) pending on Sajed's side after testing; repo stays private.

## Next step
- Fix what Sajed reports from device testing; feature requests: plan first, wait approval, branch feature/<name>.

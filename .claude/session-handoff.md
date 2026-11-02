# session-handoff

## State
- v1.12.0 on main. Since 1.8: statement links, credit limits, monthly scheduled reminder on designated gateway device, WhatsApp 970/972 (1.9); payment methods + transfer review, discounts, top customers, cash close (1.10); offline voice entry with review inbox, home widget, contacts import (1.11); multi-shop owners with switcher (1.12).
- Worker: cron backups (KV ttl 7d) + cleanup, /s/{token} statement page, /v1/shops/link. Rules deployed each phase.
- Sajed tests on device; he asked for no automated tests in these phases (only build checks).

## Decisions
- Reminder sends only from the designated gateway device (settings/sms.gatewayDevice); staff request via gateway/request + WorkManager check.
- Voice: record PCM -> WAV kept; recognition from PCM (API 33+, on-device when available); never auto-saves.
- Transfers: Tx.wallet + verify pending/ok/missing; missing excluded from income.

## Open
- Not done by choice: auto per-entry SMS, due dates/installments, cheques, multi-currency, meter OCR.
- Key rotation still pending on Sajed's side.

## Next step
- Fix what Sajed reports from device testing.

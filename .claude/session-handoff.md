# session-handoff

## State
- v1.7.0 on main. All roadmap phases after 1.3 done: business types, inventory+barcode, suppliers (1.4); menu/orders + BT printer (1.5); sizes/colors, returns, deposits (1.6); plans, payments, presence, renewals, broadcast, support view (1.7).
- Backend rules + Worker deployed with every phase. Keys in secrets/.
- Sajed tests on device himself; no automated test runs requested for 1.4-1.7.

## Decisions
- Stock qty derived from moves (like balances). Returns = negative sale + return move.
- Orders write a sale tx in the same batch; cancel deletes it (deleteTx perm).
- Printer prints Arabic as raster image (ESC/POS GS v 0).
- Support mode = ShopRoot as SuperAdmin with read-only Access.

## Open
- Device testing of 1.4-1.7 by Sajed. Key rotation (Firebase SA, Cloudflare token) still pending on his side.

## Next step
- Fix what Sajed reports.

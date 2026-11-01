# Project rules — قيد / Qayd (read first, every session)

Owner: Sajed. Reply in Arabic. Identifiers, syntax and commits in English.
Output: no greetings or filler. Max 3 bullets of explanation. One-line summary before code.

## Edit rules (strict)
- Targeted edits only. Never rewrite or reformat a whole file.
- Before editing: read docs/repo-map.md, open only the relevant file/section, state what you touch in one line.
- No refactors, renames or style changes unless asked. Preserve every feature, public name and BrandConfig key.
- After editing: run `./gradlew testDebugUnitTest assembleDebug`, update docs/repo-map.md (2 lines), commit with a clear message.
- Change > 150 lines: propose a plan first. Never remove or change a feature without approval.
- Never reintroduce a bug listed under "Solved bugs" in docs/repo-map.md.

## Workflow
- Feature request: plan, wait for approval, implement on branch feature/<name>.
- The shell (direction A) is approved. Never rebuild it; extend it.
- Session start: read .claude/session-handoff.md and docs/repo-map.md only.
- Session end: rewrite .claude/session-handoff.md (max 25 lines): state, decisions, open issues, next step.
- Version: bump VERSION_NAME in gradle.properties + version.json on substantive changes.
- Every push to main publishes a Release vX.Y.Z-bN. Keep docs/releases/vX.Y.Z.md current with three Arabic sections: الإضافات / المميزات / الإصلاحات. Commit subjects are listed automatically, so write them clearly.

## Stack
- Native Android: Kotlin + Jetpack Compose, minSdk 26, targetSdk 36. No WebView, no WebToApp.
- APK built by GitHub Actions on every push (.github/workflows/build.yml); signing via repo secrets.
- Cloud: one Firebase project for all shops (Auth + Firestore, offline persistence) + Cloudflare Worker for admin-only operations. Security Rules enforce shopId isolation, roles and paid features; client checks are cosmetic.
- SMS sending lives inside this app (gateway logic ported from Dev-saged/SMS-Barq).
- Spec: docs/SPEC.md is the source of truth for requirements and decisions.

## Architecture
- dev.saged.qayd.config.BrandConfig is the only place for brand name, OKLCH palette, currency and tunables. No colors or brand strings elsewhere.
- Balance is never stored: always derived from append-only transactions. Deletes are soft.
- PlayStation sessions store an absolute server start timestamp; the UI derives the timer.
- Each module (debts, PlayStation, electricity, messages) is enabled per shop.

## Design and security
- Icons: hand-drawn vectors in ui/icons/QIcons.kt only. No emoji, no icon packs.
- Contrast >= 4.5:1 (checked by ShellSnapshotTest), touch targets >= 44dp, visible focus, respect "remove animations".
- Numbers/signs inside Arabic text go through an LTR isolate.
- The repo is private and Sajed asked to keep all keys in it: `secrets/` holds the signing keystore, Firebase service account and Cloudflare token (see secrets/README.md). Never make the repo public, never copy these values into code, logs or release notes. `app/google-services.json` is the public client config.
- Backend changes: deploy rules with `backend/scripts/deploy-rules.mjs`, Worker with `wrangler deploy` in backend/worker, then run `backend/scripts/smoke-admin.mjs`.

## Code hygiene
- No comments revealing AI authorship. Comments only when necessary, short Arabic. No dead code, no TODOs.

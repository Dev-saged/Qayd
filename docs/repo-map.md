# repo-map — قيد

## Layout
- `app/src/main/java/dev/saged/qayd/`
  - `MainActivity.kt` — splash, edge-to-edge, sets QaydTheme + QaydShell.
  - `config/BrandConfig.kt` — CONFIG: name, tagline, currency, PS warn minutes, OKLCH palette (light/dark).
  - `ui/theme/Theme.kt` — OKLCH→sRGB, Palette, Readex font, forced RTL.
  - `ui/icons/QIcons.kt` — hand-drawn 24×24 stroke icons.
  - `ui/components/Components.kt` — motion (Silk easing, entrance, pressScale, pulse, reduced-motion), Ring, Sparkline, Ambient background, text/icon helpers, 1s ticker.
  - `ui/shell/QaydShell.kt` — tabs, bottom bar with center FAB, empty states for non-home tabs.
  - `ui/home/HomeModels.kt` — UI models + SampleHome data (until Firebase is wired).
  - `ui/home/HomeScreen.kt` — header, debt hero + sparkline, PlayStation tiles (tap = start, long-press = end), KPIs, recent activity.
- `app/src/test/.../ShellSnapshotTest.kt` — renders home light/dark to `app/build/snapshots`, asserts contrast.
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

## Solved bugs
- Signed amounts flipped ("300+") inside RTL text → wrap in LTR isolate (`ltr()` in HomeScreen).
- Light theme: dark text on gold FAB/avatar failed contrast → BrandConfig onPri light tone.

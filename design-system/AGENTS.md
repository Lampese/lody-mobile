# Mobile Design System

Shared tokens, icons and fonts for the native iOS (`../ios`) and Android
(`../android`) apps.
Principles and integration steps: [README.md](README.md).

## Invariants

- `tokens/`, `icons/manifest.json` and `runtime/` are hand-edited. `generated/`
  is produced by `node scripts/build.mjs` and committed; never edit it by hand.
- After any change run `node scripts/build.mjs && node scripts/check.mjs`.
  `check.mjs` fails on stale `generated/`, missing fonts, or a broken contrast
  floor from `tokens/contrast.json`.
- Apps reference semantic tokens only. `palette.json` is never emitted.
- Chromatic palette families are generated (OKLCH, `scripts/lib/color.mjs`) from
  one anchor each; edit anchors / `ramp`, not hex steps. Gray is hand-tuned.
- Semantic refs may carry alpha: `{indigo.600/12}`. Contrast checks composite
  translucent backgrounds onto `bg.base`.
- Every semantic color needs both `light` and `dark`.
- Icons: Lucide only, pinned by `lucideVersion`. Both platforms are emitted from
  the same path data (`scripts/lib/svg.mjs`), so geometry matches exactly. Icon
  stroke width (`stroke.icon`) is baked into the generated assets.
- Fonts live once in `fonts/font/` under Android resource names (`lody_*`); the
  Android app adds `fonts/` as a res dir and iOS registers the same files by
  `LodyFontFiles.all`. iOS addresses faces by PostScript name.
- Scripts are dependency-free Node 22. Only `sync-icons.mjs` and
  `fetch-fonts.mjs` use the network.

## Files

- `examples/{ios,android}`: native preview apps; screens are kept 1:1 across
  platforms (see `examples/README.md`).
- `scripts/lib/tokens.mjs`: loads and validates tokens, derives Inter tracking and spring physics.
- `scripts/lib/color.mjs`: OKLCH conversion and palette ramp generation.
- `scripts/lib/emit-{swift,kotlin,preview}.mjs`: platform emitters.
- `scripts/lib/outputs.mjs`: in-memory `generated/` tree shared by build/check.
- `runtime/ios/LodyText.swift`: `LodyTextStyle`, line-height modifier, font
  registration, native navigation-bar appearance.
- `runtime/ios/LodyElevation+View.swift`: `.lodyElevation(_:)` shadow modifier.
- `runtime/android/LodyTheme.kt`: `LodyTheme`, Material 3 color/type/shape and
  selection-color mapping, `LodyElevation.shadow()`.

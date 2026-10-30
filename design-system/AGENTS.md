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
- The palette is generated per mode (OKLCH, `scripts/lib/color.mjs`) from the
  web theme's key colors (`lody-oss/packages/ui/src/tokens/colors.stylex.ts`);
  change `palette.json` keys / curves, never hex steps. Step 9 of each chromatic
  scale is its key color.
- Color roles follow the web `RULES.md`: ink fills primary buttons, accent is
  state only (focus, link, on / checked, running).
- Semantic refs: `{scale.step}`, `{key.name}`, optional alpha `{accent.9/24}`;
  a string value applies to both modes. Contrast checks composite translucent
  backgrounds onto `bg.base`.
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
- `scripts/lib/color.mjs`: OKLCH conversion and per-mode scale generation.
- `scripts/lib/emit-{swift,kotlin,preview}.mjs`: platform emitters.
- `scripts/lib/outputs.mjs`: in-memory `generated/` tree shared by build/check.
- `runtime/ios/LodyText.swift`: `LodyTextStyle`, line-height modifier, font
  registration, native navigation-bar appearance.
- `runtime/ios/LodyElevation+View.swift`: `.lodyElevation(_:)` shadow modifier.
- `runtime/android/LodyTheme.kt`: `LodyTheme`, Material 3 color/type/shape and
  selection-color mapping, `LodyElevation.shadow()`.

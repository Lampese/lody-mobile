# Lody Mobile

Monorepo for the native Lody clients. Read the nearest `AGENTS.md` before editing:

- `ios/AGENTS.md`: iOS app.
- `android/AGENTS.md`: Android app.
- `design-system/AGENTS.md`: shared tokens, icons, fonts. Both apps consume its
  generated code; after changing tokens run
  `node design-system/scripts/build.mjs && node design-system/scripts/check.mjs`.

UI work on either platform uses design-system tokens and icons instead of
hard-coded colors, fonts or sizes, so both apps stay visually consistent.

`ios/.github/` workflows came from the standalone repo and do not run from a
subdirectory; move them to the root `.github/` with path filters when CI is needed.

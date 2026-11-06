# Material 3 Expressive preview

Review spike, not an approved production migration. No account access, real sends,
PR writes, or production navigation replacement. Interactions use local fixtures.

## Layout constraint

Keep the production layout unchanged. Only replace components with appropriate
M3E equivalents; preserve field order, grouping, spacing, positioning and interaction
structure. The first proposal's cards, connected reasoning buttons, extra Done button,
extended FAB and permanently expanded composer were rejected.

The revised configuration preview directly reuses production `RunConfigSheet`:
labels on the left, values on the right, and option lists expanding in place. It uses
the existing sheet configuration, palette and typography under MaterialExpressiveTheme
and MotionScheme.expressive. The composer and home bottom bar also reuse production
components. Other fixture content is illustrative, not a proposed page redesign.

The active review board contains only the corrected configuration, collapsed and
expanded. Earlier page screenshots and recording are withdrawn from review.
Screenshots are in `.ryugi/artifacts/artboard/android-m3e/pages/review`.

## Build and run

```
ANDROID_HOME=/Users/leon/Library/Android/sdk ./gradlew :app:assembleM3e
adb install -r app/build/outputs/apk/m3e/app-m3e.apk
adb shell am start -n ai.lody.android.m3e/app.lody.android.diagnostics.ExpressivePreviewActivity
```

Optional launch extras: `--es screen home|chat|config`, `--ez dark true`.
Package `ai.lody.android.m3e`, label `Lody M3E 试样`. The `m3e` build type uses a plain
Application and only the preview launcher. Existing debug/release dependencies,
application IDs, account state and code paths are unchanged. M3E preview alone pins
`androidx.compose.material3:material3:1.5.0-alpha23` for public expressive APIs; normal
builds keep the BOM's 1.4.0. Do not promote this dependency implicitly.

The configuration trial exercises MaterialExpressiveTheme, MotionScheme.expressive,
ModalBottomSheet and Switch. Existing custom rows retain their production structure;
applying the theme alone does not convert every custom control into an M3E component.

## After review

1. Map M3E components to existing controls without rearranging any page.
2. Reuse production containers and state ownership rather than rebuilding page layouts.
3. Verify small screens, dark mode, accessibility, long labels, attachments and keyboard
   positioning before migrating real business flows.

Sources:
- https://m3.material.io/blog/building-with-m3-expressive
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://github.com/material-components/material-components-android/blob/master/docs/components/ButtonGroup.md
- Official AndroidX source JAR for Material3 1.5.0-alpha23 from Google Maven.

The M3 blog is JavaScript-only in the available reader; implementation details were
checked against AndroidX source and official Android documentation.

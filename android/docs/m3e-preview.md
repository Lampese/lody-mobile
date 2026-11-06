# Material 3 Expressive preview

Review spike, not an approved production migration. No account access, real sends,
PR writes, or production navigation replacement. All text and interactions use local
fixtures; search, project/settings routes, model/agent selection and full composer
collapse/keyboard transitions are not implemented in this spike.

## Review direction

- Product: mobile coding conversations, with long-form replies and frequent context changes.
- Character: focused reading, clear actions, tactile controls.
- Existing language: Lody blue and Lucide; Android uses Material shapes and motion.
- Emphasis: new conversation, send, selected reasoning level; secondary repository actions stay tonal.
- Continuity: list/content structure and the transcript → actions → composer order remain recognizable.

Three review groups cover navigation + extended FAB, conversation composer + split
PR action, and run configuration + connected toggle shapes. Light/dark screenshots
and a native recording are in `.ryugi/artifacts/artboard/android-m3e/pages/review`.
The home FAB placement is a proposed Material adaptation for review; final navigation
placement and composer collapse need approval before replacing production UI.

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

Official APIs exercised: MaterialExpressiveTheme, MotionScheme.expressive,
SplitButtonLayout, ToggleButton with ButtonGroupDefaults connected shapes,
shape-morphing Button and FilledIconButton, ModalBottomSheet, Switch,
NavigationBar and ExtendedFloatingActionButton. Not every M3 component is new to M3E.

## After review

1. Settle Android-only theme mapping (opaque tonal surfaces, blue accent, type and motion).
2. Extract approved buttons, selection groups and sheets behind shared native wrappers.
3. Migrate navigation, creation and chat controls incrementally; preserve state ownership,
   attachment drafts, keyboard positioning and runtime behavior.
4. Verify small screens, dark mode, accessibility, long labels and real business flows.

Sources:
- https://m3.material.io/blog/building-with-m3-expressive
- https://developer.android.com/jetpack/androidx/releases/compose-material3
- https://github.com/material-components/material-components-android/blob/master/docs/components/ButtonGroup.md
- Official AndroidX source JAR for Material3 1.5.0-alpha23 from Google Maven.

The M3 blog is JavaScript-only in the available reader; implementation details were
checked against AndroidX source and official Android documentation.

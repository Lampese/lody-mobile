# Design system previews

Two native apps that render the same screens from the shared tokens, so iOS
and Android can be compared side by side:

- **Chat list** (home): the reference design for the app's main screen. Chats
  grouped by project, one line each, no separators or metadata. The indent gutter
  shows a glyph only when a chat needs attention (running, waiting, failed,
  unread); collapsed groups show a count and their most urgent state; long groups
  show the first 6 plus "Show N more". Search and new chat sit at the bottom
  (iOS 26 bottom search toolbar; a floating pill + button on Android). Swipe to
  archive (iOS) and long-press menus act in place.
- **Components** and **Tokens**: open from the More (⋯) menu, which also switches
  appearance (system / light / dark).

Screens and components are kept 1:1 between `ios/App/*.swift` and
`android/app/src/main/kotlin/app/lody/designpreview/*.kt`; change both together.
Both apps read `../generated`, `../runtime` and `../fonts` directly, so run
`node scripts/build.mjs` after editing tokens and rebuild.

## iOS

Requires Xcode 26 and XcodeGen (`brew install xcodegen`).

```sh
cd design-system/examples/ios
xcodegen generate
open LodyDesignPreview.xcodeproj
```

Run the `LodyDesignPreview` scheme on an iOS 26 simulator. A device needs a
development team set under Signing & Capabilities.

## Android

Requires Android Studio (or JDK 17 + Android SDK with platform 37).

```sh
cd design-system/examples/android
./gradlew installDebug   # or open this directory in Android Studio
```

The app installs as "Lody Design" (`app.lody.designpreview`) next to the real app.

## Status

Neither app has been compiled yet; expect small fixes on first build.

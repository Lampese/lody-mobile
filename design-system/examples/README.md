# Design system previews

Two native apps that render the same three screens from the shared tokens, so iOS
and Android can be compared side by side:

- **Sessions**: a session list with filters, section headers, state glyphs and the composer.
- **Components**: buttons, status badges, native controls (switch, segmented), inputs,
  banner, code block, diff and empty state.
- **Tokens**: type scale, all icons, all semantic colors, spacing and radius.

The sun / moon button in the top bar cycles system → light → dark.

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

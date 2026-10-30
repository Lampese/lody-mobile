# Lody Mobile

Native mobile clients for [Lody](https://github.com/loro-dev/lody).

| Directory | Contents | Imported from |
|---|---|---|
| [`ios/`](ios) | iOS app (SwiftUI) | `Innei/lody-ios` `main`, with history |
| [`android/`](android) | Android app (Kotlin, Compose) | `LodyAI/lody-android` `bootstrap`, with history |
| [`design-system/`](design-system) | Shared tokens, icons and fonts for both apps | new |

iOS and Android reuse native components but share one design language: colors,
type, spacing, icons and content layout come from `design-system/` so the two apps
look the same. See [design-system/README.md](design-system/README.md).

## Setup

```sh
git clone --recurse-submodules https://github.com/Leeeon233/lody-mobile
# or, in an existing clone:
git submodule update --init --recursive
```

Each app keeps its own build instructions in `ios/README.md` and `android/README.md`.

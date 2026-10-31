# Lody Android

- Independent Android-only client. Use Kotlin and Gradle Kotlin DSL; open the root in Android Studio.
- The iOS repository is a reference for behavior and public cloud protocol only. Do not import its build, private packages, credentials, or absolute source paths.
- UI decision: use Jetpack Compose + Foundation, with Material 3 components where appropriate. Keep product components and design tokens separate from screens. iOS and Android share design semantics and component responsibilities, but keep platform-native navigation, keyboard, accessibility and lifecycle behavior. No extra UI component library by default.
- Runtime decision: initially host the same browser cloud runtime in a native-owned WebView. Business code depends on a platform-neutral Kotlin interface; WebView, JSON bridging and watchdog internals remain inside the implementation. Preserve command/event semantics across Swift and Kotlin so a future native runtime can replace the WebView implementation.
- Keep watchdog deadlines outside JavaScript, fence callbacks by runtime generation, bound automatic restarts, suspend deadlines in background, and never replay writes after recovery. Long-lived credentials stay native; only short-lived grants may enter the runtime. A machine ACK is delivery, not completion.
- Phase 2 adds independent Device Flow and a read-only workspace catalog probe. Offline diagnostics remain separate. No sends, writes or Machine RPC are exposed; diagnostics and fault injection belong only in the debug source set.
- The vendored cloud-runtime source is an unchanged, provenance-tracked snapshot. Apply Android bridge adaptation outside that source; retain dependency licenses and reproducible asset generation.
- Use stable dependency versions, pinned in `gradle/libs.versions.toml`; AGP owns built-in Kotlin support.
- Preserve user edits. Never copy tokens or signing keys into the repository. Keep local SDK paths in ignored `local.properties`.
- Follow native Android navigation, lifecycle, accessibility, and background execution rules.
- Verify scaffold changes with `./gradlew :app:assembleDebug :app:lintDebug`. Add behavioral tests when implementing behavior; do not report NO-SOURCE tasks as passing tests.

## Phase 1 runtime contract

- `LodyApplication` owns `RuntimeOwner`; pages never create or destroy a runtime. Tie foreground/background to ProcessLifecycleOwner, not Activity callbacks. Every operation supplies its expected account/workspace scope; switching/logout cancels the old lease and observers.
- Features depend on the suspend/StateFlow `CloudRuntime` contract. Keep JSON, WebView and callback transport inside the adapter. Debug-only commands and fixture scopes stay under `src/debug`.
- Negotiate protocol/capabilities before READY. Keep native control/query/diagnostic deadlines and bounded chunk assembly; coroutine cancellation removes native waiters and cooperatively aborts JS. Cancellation is not rollback. No replay across recovery or process restart.
- Run JS, JVM owner/protocol tests and Android WebView tests for runtime changes. Snapshot drift checks are part of asset generation; update hashes and provenance only after reviewing an intentional source update. See `docs/runtime-snapshot.md`.

## Phase 2 auth and network boundaries

- AccountController owns native credentials and serializes login/restore/cancel/logout. Public UI state contains no device_code or bearer token. Use the official registered lody-cli Device Flow client until the server provisions a dedicated Android client.
- Keystore stores the AES-GCM key; only ciphertext goes to noBackupFilesDir. Invalidated keys or tampered ciphertext require fresh login. Never import desktop credentials or log tokens/server bodies.
- Native HTTP uses fixed official endpoints, bounded bodies, cancellation and no redirects/cookies/logging. NetworkRuntime is a local bundled asset whose CSP permits only the validated grant origin. Fetch and native interception both restrict requests to the selected workspace's read-only catalog streams; reject redirects, remote scripts and writes.
- The read probe starts the shared runtime without user identity or presence, avoiding implicit git-state Machine RPC. Network readiness and catalog synchronization are separate states; a WebView heartbeat never means cloud LIVE.
- Keep one app module and manual construction while these boundaries remain small. JSON belongs in adapters; do not introduce a second Streams client in native code or a dependency-injection framework for this probe.

## Catalog cache and connection recovery

- `CatalogRepository` replaces the old cloud probe controller and owns the selected workspace, one runtime subscription and the typed display projection. Pages observe its StateFlow; they never own a WebView or parse cache JSON.
- `CatalogStore` / `AccountContextStore` are suspend ports. The current AtomicFile implementation stores bounded whole snapshots under noBackupFilesDir; a future database implementation can replace it when queries require it. No credentials, CRDT replicas, cursors or send intents belong in this cache.
- Cached account/workspace context is unverified. Grant requests require online account validation. Login switching, logout and credential revocation cancel owned work before clearing projections. Network failures retain cached display, never label it LIVE.
- Only a matching catalog + synced event pair may persist. Stamp `RuntimeEmission.generation` at native receipt before buffering; never infer the origin generation at collection time. Revisions restart per generation.
- Persist with a scope-owned conflated worker; disk latency must not backpressure cloud event delivery. Cancel and join the worker before clearing/switching its scope.
- RuntimeLifecycle owns one process-lifetime network subscription. Network validation is a recovery hint for an unavailable read lease, not proof of cloud liveness; backgrounding never registers another callback or starts recovery work.

## Home UI

- `home/HomeActivity` is the launcher: sign-in (Device Flow) without an account, otherwise `home/ChatListScreen`. It reads `AccountController` / `CatalogRepository` state only and picks the first workspace when none is restored.
- UI uses the shared mobile design system in `../design-system` (Gradle adds `generated/android`, `runtime/android` and `fonts` as source / res dirs): `LodyTheme`, semantic tokens, Inter, Lucide `LodyIcons`. Do not hard-code colors, sizes or fonts in screens. Reference design: `design-system/examples/android/.../SessionListScreen.kt`; iOS counterpart: LodyKit `ChatListView`.
- `home/ChatListModel` mirrors iOS `InboxSections.projects`: pinned first, projects by name, unassigned chats last, archived hidden, 5 chats per group before "Show N more"; status strings map to running / waiting / failed attention. Keep it pure and covered by `ChatListModelTest`.
- The list stays read-only in this phase: no new-chat button, row menus or writes until sends and Machine RPC exist.


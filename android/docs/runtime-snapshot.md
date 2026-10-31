# Shared runtime source and dependency updates

`runtime/vendor/cloud-runtime` is an unchanged 64-file snapshot from Lody iOS `packages/cloud-runtime`, commit `8c741f17dbd690fcce9ae1f5bdd5f250613fe2c8`. Its license is retained. `vendor/PROVENANCE.json` identifies the source and `vendor/SHA256.json` records every file. `npm --prefix runtime run snapshot:check` and every asset build reject changed, added or missing files and symlinks. The drift test modifies a disposable copy, never the actual snapshot.

For an intentional update:

1. Select and record a reviewed upstream commit. Export only `packages/cloud-runtime` into a temporary directory using `git archive <commit> packages/cloud-runtime`; do not copy a live checkout wholesale.
2. Compare that export against the current snapshot. Review protocol changes, licensing and browser dependencies. Preserve Android adaptation in `runtime/src`, outside the snapshot.
3. Replace the snapshot only after reviewing the diff; update PROVENANCE and SHA256 together. Generate hashes with the exported `hashes()` function in `runtime/snapshot.mjs`, directed at the reviewed source directory. No build regenerates the manifest automatically.
4. Run snapshot verification, `npm ci`, JS tests, asset build, JVM and Android bridge tests. Update `runtime/contract/fixtures.json` only for an intentional contract change. Capture actual WebView/WASM results and verify release assets contain no diagnostics.

The production lockfile remains pinned. Phase 1 reported Zod advisory **GHSA-mrhx-c7xc-m3w5** against 3.25.76. Phase 2 pins **4.6.7** and verifies real shared schemas: accepted identities, strict extra-field rejection, shortcut variants, share object validation, and oversized constrained collections failing before per-item error allocation. The oversized-input test failed on v3 and passes on the patched version. `npm audit --omit=dev` reports zero known vulnerabilities for this lockfile. No source snapshot changes or v3 compatibility shim were needed.

References: https://github.com/advisories/GHSA-mrhx-c7xc-m3w5 and https://zod.dev/v4/changelog. Audit evidence is retained separately for each phase.

Bridge protocol 2 adds an explicit command capability handshake, `$cancel`, native command deadlines, and ordered message chunks. It wraps the original JS runtime; it does not change the upstream cloud protocol. Fixtures use synthetic catalog data matching the vendored event shapes, not captured user data. The offline document exposes `ping`, `setActive`, and `probeSchema`. The network document additionally advertises `connect`, `grant`, `watchSession`, and `closeSession`, used through the native scoped runtime. Session reads reuse the upstream Loro projection and emit typed text messages to Compose. Both network gates allow only same-workspace catalog/session GETs (plus HEAD and native CORS preflight); returning to the list stops the session subscription. No write capability is advertised.

Android applies `src/catalog-budget.mjs` at bundle time to raise the three catalog
input guards from 8 MiB to 16 MiB per replica. A real machine catalog with about
9.2 MiB of bootstrap input exceeded the original limit and prevented workspace
publication. The pinned source and its hashes remain unchanged; the adapter fails
the build if the expected upstream guards change. Snapshot, bootstrap updates,
and live updates share the bounded budget; the existing 100-page limit remains.
The network adapter separately caps each response at 16 MiB. Synthetic watcher
tests verify large-catalog publication and rejection beyond the new budget.

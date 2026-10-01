# Contributing to Ashva

Ashva is an Android-first early alpha. Check [current limitations](README.md) and [Relay tasks](.relay/tasks.md) before proposing a new milestone. Small, reviewed changes are welcome; discuss large architecture, data-provider or dependency changes first.

## Development

Use JDK 21, Node 22 and the Android SDK with platform 37/build tools 37.0.0. Follow the README build/check commands and [user walkthrough](docs/USER_TESTING.md). macOS/Xcode is only needed for the shared iOS compilation check. There is no iOS UI yet.

Preserve these boundaries:

- Android screens and the board use native Jetpack Compose, immutable state, lifecycle-aware Flow and unidirectional data flow. No XML screens, Views, Fragments or WebView board.
- Common KMP code stays Android-free. Database/filesystem/engine execution has platform adapters.
- Prefer latest stable compatible libraries, pinned centrally, with official release evidence and passing gates. Discuss experimental channels first.
- Keep imports, bulk decoding and analysis off the UI thread. Preserve learner data with explicit migrations; no destructive fallback.
- Wrong attempts keep the lesson board unchanged, show the expected move automatically, and switch branches only with confirmation. A legal alternative is not automatically a blunder.
- Do not edit generated pack bytes. Data updates require audited rights, a versioned importer/snapshot lock, exact dependencies, attribution and repeatable legal/checksum verification.

## Tests and reports

Run the relevant README gates and record exact results, including failures/skips. UI changes need an emulator/device smoke test. Use only a disposable test device for instrumentation: connected tests can uninstall the target APK and erase its local progress. Select the exact ANDROID_SERIAL and confirm Gradle lists only that device.

Never submit secrets, signing keys, private PGNs, personal paths or unredacted logs/screens. Run the directory secret scan and public-surface audit before sharing files. Use minimal synthetic PGN/FEN fixtures. Security issues follow [SECURITY.md](SECURITY.md), not a public detailed bug report.

## AI-assisted work

Read [AGENTS.md](AGENTS.md) and the Relay context in the stated order. Claim one task before editing; renew the claim between major stages; run gates, write a factual log and run Relay doctor before marking done. History is append-only except an explicit owner-authorized privacy redaction. Keep technical decisions and reproducible outcomes, not chat transcripts or personal machine details.

The project owner performs all Git initialization, staging, commits, remotes, pushes and publication. Agents must not perform those operations unless that policy is explicitly changed by the owner.

## Licensing and conduct

Submit only work you have the right to share under the applicable license. Unless explicitly stated otherwise, contributions intentionally submitted for inclusion in Ashva's original code/documentation/assets follow the root [Apache-2.0 LICENSE](LICENSE); data keeps its collection-specific license. Keep [NOTICE](NOTICE) and applicable upstream notices, and identify changes to redistributed modified files as required by the license. For new original source files, include `SPDX-License-Identifier: Apache-2.0` in a comment appropriate to the language; retain existing third-party headers unchanged. Do not copy commentary, artwork, engines or datasets merely because they are publicly accessible. Reassess dependency notices before distributing binaries.

Be respectful and specific in reviews. Critique behavior and evidence, not people. No harassment, personal information exposure or misleading claims about coverage, mastery, historical intent or guaranteed chess wins.

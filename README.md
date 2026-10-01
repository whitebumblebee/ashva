# Ashva ♞

An Android-first chess repertoire teacher built with native Jetpack Compose and Kotlin Multiplatform. Ashva (अश्व, “horse”) takes its name from the chess knight.

**Early alpha · Android 0.3.1.** A working teaching prototype, not a complete opening book. There is no Play Store release or playable iOS app yet.

| Learn                                             | Study a line                                                | Identify                                                                  |
| ------------------------------------------------- | ----------------------------------------------------------- | ------------------------------------------------------------------------- |
| ![Ashva learning home](docs/screenshots/home.png) | ![Ruy López study and replay](docs/screenshots/trainer.png) | ![Position-based opening identification](docs/screenshots/identifier.png) |

Screenshots are from the Android 0.3.1 alpha on an isolated API 33 emulator, using synthetic starter-lesson state. Any demo percentages are explicitly labeled.

## What you can try

- Study and practice short opening lines as White or Black.
- See the Full idea, plans and complete move list; replay, jump, pause and change speed.
- Choose a named variation and return to its exact branch point.
- Get the next-move hint automatically after an incorrect attempt. Legal off-line moves are not automatically blunders; the board stays unchanged until a correct move or confirmed switch.
- Identify positions within the starter book, including transpositions and explicit ambiguous/unknown/last-known results.
- Paste standard PGN/FEN into the identifier; imports currently open the final position, not the guided game player.
- Resume a saved lesson after cold relaunch, preserving color, cursor, branch history and hint context.
- Install both reviewed, bundled source packs into local Room/SQLite without internet or an account.

## Coverage: lessons are different from source data

| Content               | Delivered                     | Limits                                                                                                |
| --------------------- | ----------------------------- | ----------------------------------------------------------------------------------------------------- |
| Authored teaching     | 7 openings / 13 short lines   | Ruy López has 3 routes; not full repertoires or engine-reviewed theory                                |
| Opening taxonomy pack | 3,815 sequences / 3,174 names | 149 mechanically name-derived families; 1–36 half-moves; not yet connected to broader search/teaching |
| Broadcast pack        | 79 April 2020 game scores     | 31–205 half-moves; not yet an in-app GM library; 15 unresolved source-scoped player names             |

The teaching openings are Ruy López, London System, Sicilian, French, King's Indian, Queen's Gambit and Caro–Kann. Home repertoire percentages and Review/Profile statistics are **demo data**, not measured mastery. Bookmarks and actual attempt records are real; personalized recall scheduling is not implemented.

There is no Stockfish evaluation, engine-generated continuation, automatic strategic narration, full historical-game coaching, cloud sync or server. No finite database covers every possible continuation, and Ashva does not promise guaranteed wins.

See [content provenance and measured coverage](docs/CONTENT_PIPELINE.md), [offline storage](docs/OFFLINE_STORAGE.md) and [learning design](docs/LEARNING_DESIGN.md).

## Build and run

Install Android Studio/SDK (platform 37, build tools 37.0.0), JDK 21 and Node 22. Java/Kotlin bytecode targets 17. macOS with Xcode is needed only for shared iOS compilation. Set `ANDROID_HOME` to your SDK or configure an ignored `local.properties`; do not commit machine-specific paths.

From the project root:

```bash
./gradlew :androidApp:assembleDebug
adb devices -l
adb -s <your-device-serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb -s <your-device-serial> shell am start -W -n com.openinglab.app/.MainActivity
```

Replace the serial placeholder with the exact device shown by adb. The launcher name is **Ashva**; the existing `com.openinglab.app` package and database name are retained to preserve upgrades and learner data. Do not uninstall or clear app data to update.

[Step-by-step user testing](docs/USER_TESTING.md) covers phone installation, emulator setup, both colors, hints, branch replay, offline packs and cold resume.

## Stack and architecture

Native Compose UI only—no XML screens, Views, Fragments or WebView board. XML is limited to Android platform resources, including launcher/theme/backup policy.

- Kotlin 2.4.20 / AGP 9.4.1 / Gradle 9.7.1.
- Compose BOM 2026.09.00 / Material 3 / Navigation 3 1.1.7 / Lifecycle 2.11.0.
- API 37, minimum Android 8.0 (API 26).
- Room KMP 2.8.5 / SQLite 2.7.1 / KSP 2.3.12 / coroutines 1.11.0.
- Serialization 1.11.0; shared Android, JVM and iOS compilation targets.

Versions are centrally pinned in [the version catalog](gradle/libs.versions.toml), not a claim that every dependency is always the newest. Upgrades use official stable releases, compatibility checks and tests; experimental channels need an explicit decision.

`androidApp` owns Compose rendering, Navigation 3 and lifecycle-aware immutable UI state. `shared` owns legal rules, notation/PGN, position identity, lesson graphs/replay and Room repository contracts. `contentTools` imports reviewed data locally using those same rules. Analysis/import work stays off the UI thread.

## Checks

```bash
node --test scripts/*.test.mjs
node scripts/public-audit.mjs
node scripts/run-secret-scan.mjs
./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test
./gradlew :contentTools:run --args=verify
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug
# Only on a separate test device; connected tests can uninstall target data.
ANDROID_SERIAL=<isolated-test-serial> ./gradlew :androidApp:connectedDebugAndroidTest
# On macOS:
./gradlew :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64
```

The scanner downloads a checksum-pinned official Gitleaks binary into a temporary directory, prints no matched values, and removes only its own temporary directory. Automatic download supports Apple Silicon macOS and Linux x64; other platforms can run Gitleaks 8.30.1 manually with `.gitleaks.toml`. It scans directories, not Git history. Neither audit is a guarantee that no vulnerability or secret exists.

[CI](.github/workflows/ci.yml) runs build, domain/importer tests, pack verification, lint, secret/privacy checks, isolated Compose tests and shared iOS compilation. Actions are immutable-pinned with read-only repository permissions; no publishing, signing, cloud deployment or paid service is configured. Hosted CI remains unverified until the owner pushes and sees it pass.

## Contribute, privacy and licensing

Read [CONTRIBUTING](CONTRIBUTING.md), [SECURITY](SECURITY.md), [privacy](docs/PRIVACY.md) and [public-readiness notes](docs/PUBLIC_READINESS.md). Reports should use synthetic games and redact device logs.

Original Ashva code and project-authored documentation/assets are licensed under [Apache-2.0](LICENSE), Copyright 2026 Shishir Jha. See [NOTICE](NOTICE) for attribution and [license scope](LICENSES/README.md) for exclusions. This does not relicense chess data or third-party components. Opening taxonomy is CC0-1.0; broadcast exports and derived game data are CC-BY-SA-4.0. Keep source URLs, revisions, changes and attribution with redistributed data. See [third-party notices](THIRD_PARTY_NOTICES.md) and each immutable pack's manifest/ATTRIBUTION.txt.

Ashva is independent of Lichess, Chess.com and the people named in historical data; no endorsement is implied.

# Ashva alpha privacy notes

Applies to the checked-in Android 0.3.1 alpha, not a promise about future online features.

## What the app stores

The app stores lesson bookmarks (line, move cursor, color, branch-return context and hints), move-attempt records, repertoire selections and installed source packs in its private local Room/SQLite database. Pasted PGN/FEN is processed locally; current identifier input/board state is in memory and may be retained in Android activity saved state, not uploaded or added to a cloud game library.

There is no sign-in, analytics, advertising, crash-report upload, cloud sync or app-owned server. Source packs install from reviewed APK assets, not live downloads. The release manifest has no internet permission. Development/debug tooling may add internet permission; use debug builds only for local tests.

## Backups and deletion

This alpha disables automatic Android backup and explicitly excludes app storage from Android 12+ cloud/device-transfer backups. Android/OEM behavior and manual device-root or developer backups are outside the app's control; this is not an encryption guarantee. Data is not additionally encrypted by Ashva; Android's application sandbox/device protections apply.

Uninstalling or clearing app data removes bookmarks, attempts and installed content. There is no user export, account recovery or cross-device restoration yet. Reinstall updates with install -r using the same package/signing key; do not uninstall automatically to work around an installer error.

## Public source data and contributions

The bundled historical source data contains third-party public player names, event metadata and game URLs as exported by Lichess. Fifteen source-scoped player identities remain unresolved. This data is not a verified GM career archive. It retains collection-specific rights and attribution; the app does not collect a learner's name, rating or account identity. The Profile screen is visibly a neutral demo profile, not a real account.

The optional local downloader contacts approved public source URLs; the secret-scanner helper downloads a pinned official Gitleaks release. Those developer commands, Gradle/SDK dependency downloads and GitHub CI contact external services under their respective terms. They are not per-move app telemetry.

If you submit an issue, screenshot or PGN to GitHub, you choose to share it with that platform and possibly the public. Use synthetic games and remove personal/private information first. See [security reporting](../SECURITY.md).

Revisit these notes before adding any provider, remote analysis, telemetry, account, backups or sync. No Play Store Data safety submission has been made.

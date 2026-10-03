# Ashva alpha privacy notes

Applies to Android0.14.0, not future online features. Reviewed assets install locally without a network request. Ashva plans/board facts and original-move coaching are bundled or computed offline; no remote language model is called. A bounded128-entry memory cache reuses checked move explanations; cold resume recomputes them locally, not from a cloud or persistent engine cache. Courses, exact original-game bookmarks and selected repertoire revisions use local Room storage with stable legacy identities. Stockfish runs in a separate offline process; no FEN, moves or learner data go to a provider. Verified NNUE data is copied atomically into private storage; executable code remains in the APK's read-only native directory. Analysis previews are ephemeral, not cloud progress/cache. Repertoire sets store local names/color/exact member revisions and queue context; explicit edits create retained revisions, not uploads.

## What the app stores

The app stores lesson bookmarks (line/cursor/color/branch/hints/policy revision/exact original-game source), attempts, opening selections, retained repertoire revisions and packs in private Room/SQLite. Android0.15 additionally stores exact chosen review scopes/cards, actual graded events, assistance context, scheduled dates and separate Study exposure counts. Old attempts remain ungraded rather than being turned into mastery; no inferred rating or account identity is stored. All review calculations are local. It also stores explicitly followed identities and successful private PGNs, including supplied names/metadata/comments/variations. These stay learner-only, outside licensed packs/public counts/uploads. Following begins empty; unfollowing retains games. Private bounds are1000records/32MiB; excess input is rejected without pruning. See [library limits](GM_LIBRARY.md). Identifier PGN/FEN remains a separate local memory/saved-state tool. A large import draft is not put in a saved-state Bundle; successful references are small.

There is no sign-in, analytics, advertising, crash-report upload, cloud sync or deployed app-owned server. Source packs install from reviewed APK assets, not live downloads. The release manifest has no internet permission. Development/debug tooling may add internet permission; use debug builds only for local tests.

An optional [local developer service](CONTENT_SERVICE.md) is separate from Android and never automatically receives learner data. An explicitly authenticated analysis request stores its full initial FEN/move history/original move and result in the selected separate PostgreSQL service database. Result expiry is not deletion; these local records remain until operator disposal. A local token protects CPU jobs/results/metrics, not production accounts or encryption. Reviewed public pack metadata/downloads are source-attributed. Test Docker uses an ephemeral loopback-only synthetic/public-data database; do not use its trust configuration for private production data. No remote provider/cloud/explanation upload is enabled.

## Backups and deletion

Observed-move counts are derived locally from installed public score records in a disposable in-memory index. They do not collect learner identities or send positions to an external provider; rebuilding counts does not change saved repertoire choices or original games.

This alpha disables automatic Android backup and explicitly excludes app storage from Android 12+ cloud/device-transfer backups. Android/OEM behavior and manual device-root or developer backups are outside the app's control; this is not an encryption guarantee. Data is not additionally encrypted by Ashva; Android's application sandbox/device protections apply.

Uninstalling or clearing app data removes bookmarks, attempts, installed content, private games and followed-player choices. There is no user export, account recovery or cross-device restoration yet. Update with install -r using the same package/signing key; do not uninstall automatically to work around an installer error.

## Public source data and contributions

The bundled historical source data contains third-party public player names, event metadata and game URLs as exported by Lichess. The January pack has 328 unresolved source-scoped player names; the retained April fixture has 15. Counts are per-source references, not independently verified distinct people or merged aliases. This data is not a verified GM career archive. It retains collection-specific rights and attribution; the app does not collect a learner's name, rating or account identity. Android0.15 Profile shows local actual learning counts, not a real account or sample rating.

The optional local downloader contacts approved public source URLs; the secret-scanner helper downloads a pinned official Gitleaks release. Those developer commands, Gradle/SDK dependency downloads and GitHub CI contact external services under their respective terms. They are not per-move app telemetry.

If you submit an issue, screenshot or PGN to GitHub, you choose to share it with that platform and possibly the public. Use synthetic games and remove personal/private information first. See [security reporting](../SECURITY.md).

Revisit these notes before adding any provider, remote analysis, telemetry, account, backups or sync. No Play Store Data safety submission has been made.

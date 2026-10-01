---
seq: 11
agent: codex
date: "2026-10-01T14:48:36Z"
task: public-alpha-readiness
status: blocked
summary: "Prepared Ashva branding, alpha docs, preserved licenses, redacted secret scanner and pinned CI; app/content/UI gates pass, but app-license choice and historical path-redaction approval remain pending."
next: "Obtain the owner’s app-code license choice and permission to redact only personal paths in four historical Relay logs, then add the license, preserve an external backup, sanitize paths, rerun public/secret/Relay gates and finish public-alpha-readiness before repertoire-coverage."
supersedes: []
---


# public-alpha-readiness

## What I did

- Claimed the owner-prioritized publication-preparation task, not repertoire coverage. Renamed launcher/Home/Gradle branding to Ashva, versionCode 4 / 0.3.1. Preserved package, internal class/resource/database names, Room schema and learner compatibility. No Git operation, signing/account/publication, data clear or source-pack edit.
- Strengthened .gitignore for caches, local configuration, credentials/signing, databases, logs and generated APKs without hiding raw/derived content, Room schemas or wrapper.
- Added .gitleaks.toml and checksum-pinned temporary Gitleaks downloader/runner (directory mode; redacted metadata only; deletes only its own mkdtemp directory). Added read-only public-audit checks for supported ignore patterns, personal paths, local doc links, files/symlinks/size, PNG text/EXIF metadata, wrapper pin and workflow safety. Seven new Node tests; 16 total including existing downloader tests.
- Added Compose alpha/demo labeling, neutral Learner profile, explicit starter/unverified historical coverage and removal of misleading lesson count/quote attribution. Demo statistics remain samples; actual bookmarks/attempts remain real. Disabled automatic backup and excluded all learner-storage domains in Android 12+ cloud/device-transfer extraction rules (platform XML, not screens).
- Rewrote README/USER_TESTING with real counts, limitations, portable commands and owner-managed Git policy. Added CONTRIBUTING, SECURITY, PRIVACY, PUBLIC_READINESS, THIRD_PARTY_NOTICES, full applicable CC-BY-SA/Apache texts, issue forms and PR template. Preserved all immutable dataset bytes/manifests/attribution. Root app-code LICENSE is still absent pending the owner’s choice.
- Added immutable-pinned, read-only hosted CI for scanner/privacy/Relay/Node/domain/importer/pack/debug/unsigned-release/lint/Compose/shared-iOS checks. No signing, release upload, paid service or deploy. Checkout credentials not persisted; hosted disposable runners; basic open-source Gradle cache, PR read-only caches, no Build Scan upload. Added Dependabot configuration (no local scheduler/auto-merge). Pinned distribution checksum; verified existing wrapper provenance. Updated all four harness instructions to Ashva and owner-exclusive Git work.
- Added PublicAlphaTest for branding/sample labels, launcher/backup policy and synthetic-screen capture. Refreshed four documentation PNGs from the isolated emulator, inspected all visually, and recaptured Home after native splash fade. No host or learner screens captured. Updated current Relay context/gates; historical logs remain untouched.

## Commands run and their outcomes

- Initial redacted Gitleaks 8.30.1 scan found **3,799 generic-api-key false positives**, all taxonomy positionKey FEN placement. Added a narrow rule-specific exact-pack-path + FEN-shape exception, not a whole-data exclusion. Initial helper absolute paths did not match the relative-only exception; corrected boundary. Manual and reusable directory scanner then report **0 findings**, including source/raw/derived data/history. Generated/cache/Git/report directories and upstream default exclusions still apply; no Git history exists/was scanned.
- Full Gradle domain/content/debug/release/lint + both shared iOS targets: **BUILD SUCCESSFUL, 1m 7s, 139 tasks (102 executed / 7 cache / 30 up-to-date)**. JVM **76 passed**, Android-host **66 passed**, importer **16 passed**; all zero failures/errors/skips. Pack verification: **3,815 taxonomy / 79 scores**, zero duplicates/quarantine, reproduced byte-for-byte. Both shared iOS targets compile, not an iOS client/runtime test. Release APK unsigned.
- Exact contributor/CI-style JDK21 command with --no-daemon --max-workers=2 and JVM/Android-host/importer/pack/debug/release/lint gates: **BUILD SUCCESSFUL, 15s, 134 tasks (6 executed / 128 up-to-date)**. Existing test results reused, pack verifier reran. Bytecode stays Java17.
- Connected tests used **ANDROID_SERIAL=emulator-5556 only**, existing isolated API33 AVD; user emulator-5554 untouched. Initial XML showed 18 actual passes and one screenshot-fixture assumption as a failure despite Gradle success. Made screenshot fixture a regular test; rerun plus lint: **BUILD SUCCESSFUL, 2m 48s, 103 tasks (9 executed / 94 up-to-date)**. XML confirms **19 passed / 0 failures / 0 errors / 0 skipped** (12 guided +4 offline +3 public-alpha).
- Afterwards added 750ms native splash/window-fade wait to screenshot helper. Rebuilt test APK (**BUILD SUCCESSFUL, 1s**) and manually ran PublicAlphaTest#publicScreensCanBeCapturedFromSyntheticLessonState on the isolated emulator: **OK (1 test), 6.93s**. Downloaded generated PNG fixtures, visually verified settled Home. The full 19-case suite precedes this capture-only wait change; final individual capture test verifies the changed helper.
- Node regressions: **16 passed / 0 failed / 0 skipped**. actionlint 1.7.12: exit0/no diagnostics, including final SDK-prerequisite edit. Workflow has eight uses entries, all 40-character immutable pins, no secrets/PR-target/publication step. Hosted CI has **not** run.
- Merged debug/release manifests: backup false, fullBackupContent false and extraction rules present; no internet permission (only own dynamic-receiver signature permission). Lint **0 errors /4 existing update warnings**: Gradle9.7.1→9.8.0, core1.19.0→1.19.1, both Navigation3 artifacts1.1.7→1.2.0. Room expect/actual class Beta language warning remains documented/not suppressed. Release native libraries were retained without stripping; not a store-readiness assertion.
- Current public-audit **FAILS intentionally pending decisions: 172 candidates /9 findings** (8 path matches in four historical logs + missing root LICENSE). Current docs/links/PNG metadata/workflow/ignore checks have no findings. Earlier extensionless vendored LICENSE binary false positive fixed; README now explicitly says app-code license is pending instead of linking a nonexistent license.
- Relay doctor --strict passes. Logged BLOCKED, not DONE. Finalize index, release claim with owner-choice blocker and rerun doctor. No conditional gate is portrayed as passed.

## External resources touched

- Reconfirmed separate collection rights at https://database.lichess.org/#broadcasts and pinned CC0 taxonomy. Official full texts: https://creativecommons.org/licenses/by-sa/4.0/legalcode.txt and https://www.apache.org/licenses/LICENSE-2.0.txt . License-choice research: https://choosealicense.com/licenses/mit/ . No new chess fetch/provider account.
- Official Gitleaks v8.30.1 release; Apple Silicon archive SHA b40ab0ae55c505963e365f271a8d3846efbc170aa17f2607f13df610a9aeb6a5 verified before execution; Linux x64 pin from official metadata 551f6fc83ea457d62a0d98237cbad105af8d557003051f41f3e7ca7b3f2470eb. Official actionlint v1.7.12 Apple Silicon archive verified SHA aba9ced2dee8d27fecca3dc7feb1a7f9a52caefa1eb46f3271ea66b6e0e6953f. No matched secret content uploaded.
- Official tag→commit checks: checkout v7.0.1 at 3d3c42e5aac5ba805825da76410c181273ba90b1; setup-java v6.0.1 at de7274f081f381c8f8158605e0321c36c376e2e6; setup-node v7.0.0 at 820762786026740c76f36085b0efc47a31fe5020; gradle/actions v6.4.0 at 3f5f9adaf7d9fecd50b5935e54106014257a94e6; emulator-runner v2.38.0 at a421e43855164a8197daf9d8d40fe71c6996bb0d. Inspected action inputs and official Ubuntu24.04/macOS15 image SDK inventories. setup-android v4.0.4 metadata queried but action not added; both runner types already expose SDK tools.
- Official Gradle distribution SHA acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a pinned. Existing wrapper SHA 497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7 recognized in official gradle/actions checksum metadata (9.5/9.6-era wrapper); no binary replacement. Wrapper and launched-distribution release numbers need not match.
- Existing SDK/test AVD/dependency caches only. No Git initialization/status/staging/commit/remote/push, PR, public upload, account/settings change, secret rotation, deployment/payment or new GM choice.

## Risks, warnings, and what is NOT done

- **Blocked on two owner choices:** app-code MIT /Apache-2.0 /GPL-3.0 (MIT proposed), and explicit permission for privacy-only redaction of old append-only logs. Two asynchronous questions sent; no reply received by handoff. Do not invent consent, mark complete or publish a license after asking for the owner’s choice.
- Path matches are in historical 0003,0006,0009,0010. If approved, preserve untouched originals OUTSIDE the publishable project; replace only home/project/SDK/temp paths with portable placeholders; retain technical facts/front matter/counts. Log the owner-authorized privacy exception. Never put private backup/original paths in public history, compact/delete old logs or hide history from the scan. Preserve required host-only AVD locations in ignored local config before sanitizing old runtime instructions.
- Add selected root LICENSE; update README/PROJECT/PUBLIC_READINESS pending wording; retain separate data/tooling notices. Rerun public/secret/Node/Relay gates, then log DONE/relay done. Owner choosing to retain paths publicly requires an explicit documented scoped privacy exception, not silent removal from scans.
- Source-alpha readiness is not full repertoires, signed/store release readiness, trademark clearance, complete binary transitive-license inventory, penetration testing, verified GM history or hosted-CI evidence. Four lint update warnings remain; no unrelated upgrades.
- User emulator-5554 still has v0.3.0; intentionally not updated/instrumented. v0.3.1 APK built. If owner asks to test, update install -r with same package/key, never uninstall/clear automatically. Connected tests only on isolated serial. Isolated emulator stopped at handoff, existing files retained.
- Scanner defaults omit some binaries/images; no pixel/staged/deleted-history audit or secret-free guarantee. Public ignore parser is constrained/tested, not all Git syntax. Owner reviews their exact staged files/history and configures GitHub private reporting/push protection/branch rules before publication.
- Temporary tools/reports stay outside source; reusable helper deletes its own temp folder only. Do not publish audit backups or generated APKs. No original learner or source data was deleted.

## Next

Obtain the owner’s app-code license choice and explicit historical path-redaction approval. Finish only those remaining public-alpha-readiness items, preserving external originals, separate licenses and owner-exclusive Git work; rerun public/secret/Relay gates and mark done. Repertoire-coverage remains the next development milestone afterward.

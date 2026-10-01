# Public source readiness — Ashva Android alpha

This is a preparation record, not permission for an agent to initialize Git, commit, push or publish. The project owner performs every Git operation and repository/account setting change. No public repository URL has been assumed.

## Scope

The source is intended to be shared as an **early Android alpha**, not as a completed repertoire service, signed app-store release or security-audited product. Branding is Ashva; package/class/database identifiers remain compatible with earlier Opening Lab builds. Name/domain/trademark availability has not been cleared.

## Prepared locally

- Ashva launcher/Home branding, neutral demo profile, visibly labeled sample stats/unfinished game cards and version 0.3.1.
- README with real teaching/source coverage and limits, portable build/user-test instructions and current emulator screenshots.
- Owner-selected Apache-2.0 root LICENSE and Ashva NOTICE for original code/documentation/assets, plus separate source/data/dependency notices; immutable CC0 taxonomy and CC-BY-SA game assets/attribution retained. Full applicable license texts included. No external engine binary or Masters collection added.
- Ignore rules for Kotlin/Gradle/IDE/build caches, local configuration, credentials, signing keys, app databases, device logs and generated APKs. Necessary source packs, raw exports, wrapper JAR and database migration schemas remain publishable.
- CONTRIBUTING, SECURITY, privacy notes, issue forms and PR template. Android automatic/cloud/device-transfer backup policy excludes local learner storage.
- Repeatable directory secret scan with checksum-pinned Gitleaks 8.30.1 and redacted output; read-only public-file/privacy/link/metadata/workflow audit with regression tests. Gitleaks' generic-key rule has one narrow exception for valid FEN placement in the exact immutable taxonomy file, not a blanket data exclusion.
- CI for debug/unsigned release, lint, shared/domain/importer tests, byte-for-byte pack verification, isolated Android Compose tests, public/secret/Relay checks and both shared iOS compile targets. Immutable action pins, read-only token, no checkout credential persistence, no signing/deploy/release uploads, hosted disposable runners, basic open-source Gradle cache and no Build Scan publication.
- Gradle distribution checksum pinned; the existing wrapper JAR is recognized in the official Gradle wrapper-validation catalog. A wrapper JAR need not have the same release number as the Gradle distribution it launches.

## Owner decisions recorded

The owner explicitly selected Apache-2.0 on 2026-10-01. The root [LICENSE](../LICENSE) contains the unmodified official terms; [NOTICE](../NOTICE) identifies Ashva and Copyright 2026 Shishir Jha. Original code and project-authored documentation/assets are covered, while third-party chess data, dependencies and vendored Relay keep their separate licenses. The standard license appendix remains an example, not a second project-specific copyright declaration. This was a local licensing change, not a Git operation or publication.

The owner explicitly authorized privacy-only path redaction on 2026-10-01. Four historical Relay logs now use `<project-root>`, `<android-sdk>`, `<isolated-test-avd-directory>` and `<pre-release-pack-backup-directory>` instead of personal machine paths. Technical outcomes and historical status remain unchanged. An untouched copy of the prior history is outside the project; its private location and local runtime paths are recorded only in ignored `.public-audit/local-environment.json`. That file is not for publication. The external backup is temporary storage, not a durable archive. Historical commands containing these placeholders require substitution before execution.

## Current verification (2026-10-01)

Prior app verification is recorded in Relay log 0011: local debug and unsigned release builds and lint passed; shared iOS device/simulator code compiles, but no iOS app/runtime is claimed. JVM tests: 76 passed; Android-host tests: 66 passed (common suite on a second target); importer: 16 passed; isolated Compose suite: 19 passed, zero failures/errors/skips. Both source packs reproduced byte-for-byte (3,815 taxonomy records / 79 scores). Licensing-only changes do not change the app or pack bytes; no fresh app-runtime claim is made by applying the license.

Gitleaks directory scan: zero remaining findings after the narrow FEN false-positive exception. Workflow validation with actionlint 1.7.12: passed. Release merged manifest: no internet permission, backup disabled and extraction rules present. Four existing update lint warnings remain (Gradle, core-ktx and both Navigation3 artifacts); Room's expect/actual language warning is documented, not suppressed.

The remaining root-license blocker is resolved. The eight prior personal-path findings in four historical Relay logs were cleared by owner-authorized redaction. After license application, Node regressions passed **17 / 0 failed / 0 skipped**; the public-file audit and redacted Gitleaks scan each reported **zero findings**; CI syntax and Relay strict checks passed. Final results are recorded in the completion handoff; rerun them against any files you change before publication. No Git history exists or was scanned. Hosted CI still needs a real owner-managed push/run, so this preparation is not an unconditional release or security certification.

No legal/trademark clearance, complete transitive binary-license inventory, vulnerability penetration test, signed production release or store-compliance review is claimed. Source publication is distinct from APK redistribution; recheck all dependency-specific notices/signing requirements before distributing production binaries.

## Final checks before your first public push

From the root of the exact folder you plan to publish:

```bash
node --test scripts/*.test.mjs
node scripts/public-audit.mjs
node scripts/run-secret-scan.mjs
node .agents/skills/relay/bin/relay.mjs doctor --strict
./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test
./gradlew :contentTools:run --args=verify
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug
```

Run Compose instrumentation only on an isolated device using its exact ANDROID_SERIAL. On macOS, compile shared iOS targets too. A scan passing yesterday does not validate changes you add today.

Owner-only checks (agents must not perform Git operations):

1. Inspect the exact staged files/diff yourself. Ensure no ignored cache, local.properties, credential, keystore, database, local audit backup or generated APK was force-added. .gitignore does not remove files already tracked/staged.
2. If you create/import Git history, scan that history separately before publishing. The current automated scanner checks working-tree directories only; there was no Git history to inspect during preparation.
3. Confirm the root app-code license is your selected license. Preserve separate data licenses/manifests/raw attribution and vendored Relay's license. Do not treat public visibility as permission to reuse unlicensed material.
4. Review every included screenshot yourself for private information, and the README's alpha coverage wording. Re-run checks against any final edits.
5. In GitHub, enable private vulnerability reporting and secret scanning/push protection where available; restrict workflow permissions and avoid adding production secrets/self-hosted public-PR runners. Set branch protection/required checks only after the first CI run establishes actual check names.
6. Push using your own Git workflow, then inspect both hosted jobs. CI syntax and local equivalents can be checked before publication, but hosted SDK images/runners/network/cache behavior is not proven until the workflow runs there. Do not advertise a passing badge before it passes.

Private reporting/profile settings are owner-controlled account operations, not something these files silently enable. Dependabot configuration is ready for after the owner publishes/enables it; it does not update or merge dependencies locally.

## Audit limits

Scans print filenames/rule identifiers, never matched credential values. Temporary scanner archives/reports are outside the project and deleted by the helper. It downloads only the official pinned release and checks SHA-256 before execution; no Git commands or external secret upload occurs.

Public audit follows the checked-in simple .gitignore patterns (tested; unsupported syntax fails closed), checks file types/symlinks/size, personal machine paths, local doc links, screenshot text/EXIF metadata, wrapper pin and workflow safety. It cannot inspect screenshot pixels, detect every possible secret, certify license ownership, find deleted Git-history secrets or override an owner's staging decisions. Gitleaks uses upstream default rules plus the documented exception; default file exclusions still apply.

Existing dependency-update lint warnings and Room's Kotlin expect/actual-class Beta warning are not suppressed. Stable Room libraries are used; no alpha Room dependency was adopted. No performance/accessibility equivalence to leading chess apps is claimed.

---
seq: 16
agent: codex
date: "2026-10-01T21:25:25Z"
task: repertoire-coverage
status: partial
summary: "Added v0.5.0 family/color repertoire policies, finite coverage gaps, Compose editor and filtered practice with additive Room schema 3 and exact old-revision cold restore; broader coverage remains partial."
next: "Add source-attributed observed-reply counts from the existing licensed 79-game broadcast fixture to repertoire coverage, explicitly labeled with month/sample limits rather than popular-master statistics."
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

Continued the existing repertoire-coverage claim and implemented Android v0.5.0 (versionCode 6), without changing package/database identifiers, source-pack bytes or owner Git work.

Added shared RepertoirePolicy/RepertoireBook: separate family/color policies, one preferred learner move per normalized position, included opponent replies, explicit route seeding/adoption and schema/bounds/graph/side/move validation. Identity binds to a deterministic moves-only snapshot SHA, not mutable narration. Coverage traverses a finite trie of original recorded move-order prefixes, including transpositions/repetition without infinite traversal or invented original routes. It reports fitting paths, unanswered learner/reply choices, missing source continuations for a specific move order, excluded known replies and reached source endpoints. These counts are not all-theory coverage or mastery. LessonGraph can restrict practice to fitting original paths without altering the broad explorer.

Room schema 3 adds immutable repertoire_policy_versions and active_repertoire_policies. All builders register additive 2→3 alongside 1→2; no destructive fallback/conversion/deletion. Saves compare-and-set the exact next revision atomically, reject concurrent conflicting writes, retain prior versions and accept logically identical retries without rolling the pointer backward. Existing legacy active-route selections stay separate. Bookmarks optionally reference an exact policy revision; old bookmark JSON retains defaults. Cold restore reconstructs that revision's restricted graph and exact replay fingerprint/context after newer edits.

Added native Compose RepertoireScreen/MyRepertoiresScreen and Navigation3 routes: trainer Build / edit entry, Home saved-policy shelf, read-only board with replay cursor, searchable source-route picker, preferred-move radio choices, reply checkboxes/include-all, deliberate confirmed route adoption, finite coverage/off-book labels and paginated gap navigation. Practice starts on the longest fitting recorded route and offers only admitted branches; color is fixed because the other color has a separate policy. Existing hints, study/replay/branch-return and broad exploration remain. Source plans are still explicitly unavailable. Graph/policy/coverage/legal-count preparation is off Main. Editor controls wait for save, errors retain the previous policy, and rapid policy navigation joins pending saves and only publishes the last requested editor. SavedStateHandle stores the editor cursor; Room holds durable choices.

Added eight common policy tests, three JVM storage cases plus expanded real v1/v2 migration fixtures, and six Compose policy/recreation/navigation/source/cold-restore cases. Updated README, coverage/storage/pipeline/privacy/design/testing/readiness docs and Relay truth. The larger task remains partial for popular-game populations, deeper scope, reviewed plans and multi-opening aggregation. Existing screenshots remain explicitly v0.3.1 starter captures; no new screenshot/privacy claim.

## Commands run and their outcomes

- Final complete gate: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest` — **BUILD SUCCESSFUL**, 7m10s. **91 JVM / 78 Android-host / 17 importer / 28 Compose tests passed**, zero failures/errors/skips. Both shared iOS targets compile, not an iOS client/runtime. Lint **0 errors / 4 existing update warnings**; Room expect/actual-class language warning remains. Debug/unsigned release artifacts remain local, approximately 25/18 MiB.
- Initial cross-platform gate failed on two JVM-only putIfAbsent calls in new common code. Replaced them with portable getOrPut and repeated successfully. Initial Android compilation/lint had passed; no validation requirement was removed to make the native build pass.
- Focused first policy suite: **4 passed / 0 failed**. First complete regression: **26 passed / 0 failed**. After adding save/navigation hardening, concurrency and recreation cases, repeated the entire final-source gate above: **28 passed**. All UI runs targeted only the disposable API33 emulator serial 5556, not the owner's learning install.
- Source verification reproduced **3,815 taxonomy / 79 broadcast records**, zero duplicates/quarantine, byte-for-byte unchanged. Source-policy tests retain all **235 Ruy Lopez** explorer paths and build/practice both colors. Tests cover White Berlin reply gaps, Black legal off-line hints without a branch, exact old revision/cursor restoration, concurrent saves, editor recreation and switching colors during a pending save.
- `node --test scripts/*.test.mjs` — **20 passed / 0 failed / 0 skipped**. `node scripts/public-audit.mjs` and redacted `node scripts/run-secret-scan.mjs` pass with zero findings, repeated after this log. actionlint 1.7.12 — **exit 0 / no diagnostics**. Final portable context/local Relay doctor: **16 logs / 19 tasks / all checks passed**. These are local checks; no hosted CI/publication claim.

## External resources touched

Only the existing local CC0 taxonomy pack lichess-openings-c67912be581f-import-v1 (upstream c67912be581f0793dbaa776be5ccf111e01f88d9) and CC-BY-SA broadcast pack lichess-broadcast-2020-04-2020-04-snap-import-v1 were used; no chess dataset/provider was fetched or modified. Pinned official Gitleaks download was used for redacted directory checks. Reused the private disposable test AVD from 0015; local runtime paths remain in ignored audit configuration, not public docs. Emulator 5556 was shut down after gates; its reusable AVD is retained. The owner emulator/data were not started, installed, instrumented, cleared or removed. Unique synthetic test databases were cleaned up by tests; connected runners may uninstall their own disposable target. Relay/AGP automatically read existing HEAD metadata; no direct Git operation/mutation, commit, remote, push, upload, paid service, account change or publication by the agent.

## Risks, warnings, and what is NOT done

Task remains IN_PROGRESS, not a full repertoire/course claim. Policies are one family/color/snapshot each; no multi-opening aggregation or multiple named style profiles per family yet. A single preferred learner move can intentionally exclude other legal choices. Selected replies with no chosen response create a gap; normalized choices absent in an original move-order prefix report a source-continuation gap rather than synthesizing a new source record. Short source endpoints can fit a policy even when longer admitted prefixes still have gaps. Practice includes complete fitting original routes, not partially authored continuations; the broad explorer remains the place to study excluded routes. Repetition with conflicting same-position learner moves is reported, not looped forever.

No game-frequency/popularity population, new master archive, reviewed source plan, engine, off-book analysis, GM shelf/coach, recall scheduler, cloud service or iOS UI was added. Existing rights/provider/budget decisions remain. Source policies bind to exact versions; missing/inactive content retains rows and reports unavailable, with no automatic retained-version selection or upgrade mapping. Earlier policy revisions are retained for bookmarks and may grow storage; pruning/export/version-management UI remain future work. Session-only preview policies are not a substitute for Room cold persistence. Wait for saved confirmations before terminating.

No new library dependency/channel was adopted. Existing update/language warnings are unsuppressed; no representative-device frame/memory/accessibility parity benchmark is claimed. No Git history scan or hosted CI execution occurred. Use only isolated exact-serial connected tests because the runner may remove target data.

## Next

Add source-attributed observed-reply counts from the existing licensed 79-game broadcast fixture to repertoire coverage, explicitly labeled with April 2020/79-score sample limits rather than popular-master statistics. Begin with a shared legal position/reply aggregation contract and fixtures (including transpositions/repeated-position denominators), then repository/UI provenance and unavailable states. Do not fetch a new collection or infer missing redistribution rights. Preserve the exact taxonomy dependency, original game scores, separate data licenses and all learner policies/bookmarks. Renew repertoire-coverage; engine feasibility may proceed under its separate task when selected, but neither this policy slice nor the small fixture population completes broader repertoire coverage.

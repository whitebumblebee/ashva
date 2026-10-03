---
seq: 20
agent: codex
date: "2026-10-02T12:16:09Z"
task: repertoire-coverage
status: blocked
summary: "Added Android 0.8.0 read-only combined White/Black repertoire overview, validated reachable unions, explicit conflicts/editor links and retained unavailable members; all 38 UI tests pass, while course/release scope remains unresolved."
next: "Owner confirms the first teaching-course scope and measurable depth/branch target; then continue repertoire-coverage with deeper explained both-color content and cleared sample scope, not another taxonomy completeness claim."
supersedes: []
git_head: cba53d25fcf1
---

# repertoire-coverage

## What I did

After completing engine-alternatives with log/done 0019 and notifying the owner, immediately claimed repertoire-coverage as requested. Added the local Android 0.8.0 / versionCode 9 read-only multi-family overview slice. It is not completion of the broader coverage/teaching task. No source bytes, schema 3, package/database identifiers, saved policies or learner bookmarks were changed. The pending first-course scope question was not answered by the separate Stockfish distribution reply.

Added common RepertoireOverviewBuilder/models and eight common tests. Each family validates against its exact moves-only snapshot. RepertoireCoverage now reports reached admitted-prefix position keys, with cancellation checkpoints. The builder unions normalized reached positions/included replies and records all conflicting learner moves with SAN/UCI and policy/path/ply origins. Dormant preferences after an excluded prefix do not inflate checked coverage. White/Black remain separate; duplicate IDs/wrong color/invalid versions fail closed. Missing/changed families remain explicit members, with no invented counts. Bounds of 256 members and 100,000 combined reached-position/reply entries fail explicitly, never truncate.

AppViewModel builds/checks/summarizes on cancellable workers, one graph at a time; the result retains no graph collection. Policy/catalog/generation checks reject stale publication. My repertoires now displays White/Black combined summaries, conflicts, per-member fitting routes/gaps/endpoints/excluded replies and expandable exact identities. Conflict links open the actual family position and deliberately override an older remembered cursor, without saving any choice. Incompatible first moves may remain separate family repertoires; no silent winner, route stitching or automatic update. Existing per-family practice and immutable revisions remain unchanged. No new Room tables or learner writes: cold launches recompute from existing policies.

Added three Android cases for actual Room policies, White/Black/conflicts/editor link overriding saved cursor, cold-reopened missing/changed policies without rebinding, and loading/error/retry. Updated README/testing/coverage/privacy/readiness and Relay truth. The broader task still lacks deeper agreed course scope, reviewed White/Black plans, broader cleared/popular sample populations and named coherent multi-family selection/unified practice.

## Commands run and their outcomes

- Final gate: `ANDROID_SERIAL=emulator-5556 ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest` — **BUILD SUCCESSFUL / 7m6s**. **125 JVM / 98 Android-host / 17 importer / 38 complete Android tests passed**, zero failures/errors/skips. Both shared iOS targets compile, not iOS runtime/client proof. Lint **0 errors / 4 pre-existing update warnings**; Room expect/actual Beta warning remains.
- Initial focused overview instrumentation: **1 passed / 2 failed**, both new fixtures incorrectly used london-system rather than the stable existing london ID. Fixed test fixtures only, never migrated the app ID. Focused repeat **3 passed / 0 failed / 0 errors / 0 skipped / 6.636s**, then final complete suite above. No gates weakened.
- Pack integrity reproduced **3,815 taxonomy / 79 broadcast scores**, zero duplicates/quarantine and unchanged bytes. Debug metadata: **com.openinglab.app / code9 / 0.8.0 / min26 / target37**. Both actual APK engine/source/NNUE/recipe hashes and required notices verify with scripts/verify-engine-apk.mjs.
- `node --test scripts/*.test.mjs` — **24 passed / 0 failed / 0 skipped**. Public audit before this log: **191 candidate files / zero findings**. Pinned redacted Gitleaks **8.30.1 / zero findings**. Official actionlint **1.7.12 / exit0 / no diagnostics**. Portable Relay context and local strict doctor pass before log; repeated after completed history/task edits. No hosted CI/publication.
- Stopped only the isolated agent test emulator-5556, then visibly reopened the existing learning AVD on emulator-5554 without wiping/snapshot deletion. `adb -s emulator-5554 install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk` — **Success**. `am start -W` — **Status ok / COLD / complete / 3967ms total**. Package inspection confirms **versionCode9 / versionName0.8.0**; activity inspection confirms **topResumedActivity MainActivity / visible=true**. No instrumentation, uninstall, clear or learner-database inspection on this device; leave it open for the owner.

## External resources touched

Only existing licensed taxonomy/broadcast packs, prepared Stockfish assets and installed Android SDK/image were used. No new chess dataset, annotation corpus, provider account, service, paid model or cloud change. Downloaded the official pinned redacted scanner for local gates. Temporary AVD/build/scanner paths remain private/ignored. Relay/AGP metadata reads are not an owner history audit. The earlier upstream make index-refresh exception and subsequent disabling are recorded in 0019; no new direct Git mutation, staging, commit, branch, remote, push or publication during this coverage slice.

## Risks, warnings, and what is NOT done

The overview is a live read-only aggregation, not a named multi-family repertoire with explicit membership or a unified teaching/practice graph. Conflicting root choices such as e4/d4 are often intentional separate repertoires; do not classify them as bad chess or automatically resolve by overwriting/removing a family. Member fitting-route counts can overlap across sources and are not summed as unique games. Checked union counts concern finite admitted prefixes only; a source endpoint is not theory completion/mastery or the end of a game. Missing/changed active catalog versions retain exact saved policies, not automatic rebinding. Very large overview scope explicitly errors; no representative-device benchmark or unconditional scalability claim.

Product-scope blocker: the owner must choose the first deeper teaching-course/release target and its named-branch/depth scope before this broad task can honestly be completed. An async question proposing deep Ruy López for both colors, London both colors or initial teaching across the seven starter openings is still unanswered. The user authorized the separate Stockfish distribution approach, not a course-scope decision or uncleared/population data rights. Recommended next target is a genuinely explained Ruy López course for both colors, followed by the other popular openings; this is a proposal, not an owner-approved complete-release inventory. Broader popular population rights/sample scope and coherent multi-family selection/practice still need work. Keep grounded-teaching-content dependent on real coverage/contracts; do not falsely complete the parent to unlock it.

All original main-app teaching limits remain: seven authored openings/thirteen short starter lines; 3,815 taxonomy endpoints lack reviewed strategy; the 79-score fixture is not a representative master book or GM career shelf. Engine hypotheses are not explanations or guaranteed wins. No paid LLM, service, GM roster, recall scheduler, iOS client or actual publication. Owner handles every Git/publication operation. The owner's visible learning emulator must not become an automated test target.

Read-only follow-up inspection found that engine analysis currently preserves the attempt-assistance flag even when it exposes the lesson continuation. Added this to the existing persistent-recall-review acceptance notes, not an out-of-scope code change or a mastery claim. Future review must distinguish successful exposed analysis from failed/stopped requests. Current demo mastery/Review/Profile remain explicitly not actual recall assessment.

## Next

Owner confirms the first teaching-course scope and measurable depth/branch target; then continue repertoire-coverage with deeper explained both-color content and cleared sample scope, not another taxonomy completeness claim. Resume the same open task with relay claim repertoire-coverage --agent codex after recording the decision; preserve immutable sources/versions and both-color identity. Do not skip unfinished acceptance by marking the overview full coverage. Use gates → task history → done → notify → next only when the entire task is actually complete.

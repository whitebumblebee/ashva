---
seq: 32
agent: codex
date: "2026-10-02T22:12:07Z"
task: content-analysis-service
status: done
summary: "Verified local Ktor/PostgreSQL versioned content API and durable bounded UCI/import queue;19 service cases, real CIO process and all56 native regressions pass. No Android upload/deployment; iOS owner-deferred."
next: "Claim persistent-recall-review and replace demo Review/Profile with durable branch-aware recall scheduling, preserving legacy attempts and accounting for successful engine/hint assistance; leave ios-client untouched."
supersedes: []
git_head: cba53d25fcf1
---

# content-analysis-service

## What I did

Added the optional contentService JVM module with pinned stable Ktor3.6.0/CIO and pgJDBC42.7.13/PostgreSQL18.6. Android/common runtime dependencies, app version0.14/code15, package/database IDs, source bytes and learner schema5 remain unchanged. APIs provide full source manifests, exact retained object downloads/ETags, literal metadata search, snapshot-bound keyset cursors and retained catalog descriptor/file deltas. Reuses the complete legal/provenance/count/dependency pack validator in temporary Room databases, never the owner learner DB. Trusted hashes allow only the three already reviewed packs. PostgreSQL immutable metadata/active pointers and import success share a lease-guarded atomic commit; failures/cancellation/stale leases cannot replace prior content.

Durable1000-job capacity with explicit refusal, one local worker, exact-key deduplication, three bounded attempts, backoff/lease recovery/current-lease completion, cancellation, sanitised errors/count metrics, seven-day analysis result expiry and server-side configuration. Cache keys/envelopes retain full initial FEN/clocks/history/original move/budgets/protocol/exact binary and NNUE. Restarting with a changed engine fails old work before execution instead of poisoning its old key. Output is legally/SAN/root/identity/budget/terminal/coherence checked before caching. JSON bodies, reads, connection work and admissions are bounded; private job endpoints use a random local token and deny browser Origin requests. No request/raw exception/credential logging backend is installed; metrics supply bounded observability.

Added explicit separate host Stockfish build/adapter using reviewed corresponding source, Git-disabling patch, supplied NNUE and standard UCI only. Exact host identity/source/network/recipe/GPL/AUTHORS are retained; hashes are rechecked before each process, output is bounded, cancellation closes/reaps it. No linking/JNI/new source download. Added reproducible Docker test lifecycle, real PostgreSQL/HTTP/UCI fixtures and actual CIO-process smoke test. ProviderBackoff is a fake-clock-tested, disabled-provider contract with spacing/numeric-date Retry-After/minimum full-minute429/three attempts/long-delay deferral, not new collection ingestion.

README, new CONTENT_SERVICE, PRIVACY, dependency/data notices, local gates and pinned read-only CI are updated. Owner approved starting Docker and a temporary test-only DB. All test-created containers/processes were stopped/removed; synthetic/public tmpfs data is reproducible, unrelated Docker resources left alone. Ignored object/build diagnostics remain. Owner's learning emulator was neither installed/cleared/uninstalled/instrumented nor read for learner rows; it remains the verified0.14 install. New owner direction leaves ios-client TODO/untouched while finishing other remaining milestones.

## Commands run and their outcomes

- Official release/license verification before version pins: Ktor3.6.0, PostgreSQL18.6, pgJDBC42.7.13; dependency resolution **SUCCESS14s**. Initial compile failures: missing exported Room superclass, incorrect Room catalog accessor, cross-module nullable smart cast in a test, non-void JUnit test expressions, and distribution CopySpec syntax. Fixed without changing shared/app contracts; deprecated Ktor reads replaced with bounded Long readBuffer.
- Initial service integration: **7unit pass;4/7integration pass**. Three pack cases failed because temporary Room retained validation.db.lck; guarded imports stayed pending/error, not false successes. Added deletion of that exact generated lock file after DB close. Subsequent7/7integration success, then expanded coverage11/11 and final12/12.
- node scripts/prepare-service-engine.mjs: **SUCCESS**, local clang non-PGO host executable from reviewed patched source, retained exact identities/source/network/notices. No agent Git command or compiler download.
- Final node scripts/test-content-service.mjs: **BUILD SUCCESSFUL43s;7unit/12real PostgreSQL-HTTP-engine cases,0failures/errors/skips**, runnable installDist; **actual loopback CIO-process import/search/real UCI analysis SUCCESS** and its unique labeled tmpfs container/process cleanup SUCCESS. Actual engine fixtures include White originalBb5, Black originalc5, full-history repetition terminal and cache reuse. Covers concurrency/dedup/cold restart/expiry, failure/retry bounds, identity drift, cancellation/stale leases, source/dependency/immutable conflicts, capacity refusal, integrity, pagination/deltas/attribution, authorization/body/rate/unavailable states. Both source samples are queried as936 records; Ruy235 routes, not invented population coverage.
- ANDROID_SERIAL=<isolated-test-serial> ./gradlew :shared:jvmTest :shared:testAndroidHostTest :contentTools:test :contentTools:run --args=verify :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:lintDebug :shared:compileKotlinIosSimulatorArm64 :shared:compileKotlinIosArm64 :androidApp:connectedDebugAndroidTest: **SUCCESS13m4s;162JVM/128Android-host/24importer/all56native,0failures/errors/skips**. Only isolated API33 Ashva_Source_Test ran; some exact-input build artifacts were cache/up-to-date. Whole-January install/cold rebuild cumulative241.732s within unchanged per-stage waits. Both app packages still0.14/code15; no mobile feature/version increment from this server-only milestone.
- Separate January verify: **SUCCESS14s;857accepted/0duplicates/95quarantined,1–253plies**, byte-identical. Default3815taxonomy/79April/0quarantine unchanged. Both APK engine/source/NNUE/recipe/notices checks **SUCCESS**.
-28Node cases **pass**, public surface264files/**0findings**, redacted Gitleaks8.30.1/**0findings**, pinned actionlint/**exit0**, strict doctor/**pass** before final log. Final log/context/public/doctor are checked again before marking done; no hosted CI or distribution result is claimed.

## External resources touched

Official ktor.io release documentation, postgresql.org release/SELECT/SKIP LOCKED documentation, jdbc.postgresql.org download documentation and upstream Ktor/pgJDBC license texts. Maven dependency downloads; already reviewed Lichess/Stockfish inputs. Owner-approved Docker startup; postgres18.6-alpine image digest77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873 and synthetic/public-only ashva_test namespaces. No external database/account/paid provider/cloud bucket/live API collection, Git/publication or deployment.

## Risks, warnings, and what is NOT done

Completes **local service validation**, not production rollout, continuous remote acquisition or the whole goal. Filesystem content-addressed storage is local, not a hosted S3 adapter; deltas are descriptors/files, not binary patches. One worker per process, not a distributed global quota. Retained job capacity deliberately refuses at1000 instead of pruning; expiry hides outputs but does not erase stored requests. Deployment needs TLS/auth/hosting/budget/storage/archival/rights/security/load decisions, none made here. Trust authentication belongs only to ephemeral test containers, never private production data. Android is not connected to this API and uploads nothing automatically. ProviderBackoff has no enabled remote provider. Exact host binary hashes vary by platform/toolchain; this is local prepared identity, not a universal downloadable binary pin or App Store/license certification. Existing Room expect/actual Beta/compiler and native strip warnings remain; local service defaults to no SLF4J logging provider rather than emitting sensitive diagnostics. No physical-device/frame/battery/hosted-CI claim. ios-client stays owner-deferred TODO; persistent recall/Profile demos and Compose board quality remain next.

## Next

Claim persistent-recall-review and replace demo Review/Profile with durable branch-aware recall scheduling, preserving legacy attempts and accounting for successful engine/hint assistance; leave ios-client untouched. Notify the owner of this completed milestone, then immediately claim the next task. Start with deterministic shared review/target contracts and additive learner storage/tests before native Review/Profile integration. Preserve exact retained policy/set/source identities and distinguish new demonstrated recall from legacy/study/hypothetical engine exposure; no data pruning or fake mastery.

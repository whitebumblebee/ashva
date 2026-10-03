# Offline analysis — Android 0.7.0

Ashva uses separate Stockfish 19 executables through standard text UCI, without JNI/linking or C++ modifications. The owner approved this on 2026-10-02. Ashva code remains Apache-2.0; Stockfish/source/NNUE retain GPL-3.0-or-later terms. Separation follows [Stockfish guidance](https://official-stockfish.github.io/docs/stockfish-wiki/Developers.html#terms-of-use), not a blanket legal clearance of future integrations.

## Reproducible preparation

Install Node22, JDK21, Android SDK/platform37/build tools37.0.0 and NDK **30.0.16248370**, the [current LTS](https://developer.android.com/ndk/downloads) checked 2026-10-02. Preparation supports macOS/Linux (macOS needs Xcode command-line make), not Windows.

```bash
export ASHVA_NDK_DIR="$ANDROID_HOME/ndk/30.0.16248370"
node scripts/prepare-stockfish.mjs
node scripts/prepare-stockfish.mjs --verify
./gradlew :androidApp:assembleDebug
node scripts/verify-engine-apk.mjs androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Alternatively use a private unpacked NDK path; never record it in public source. [The lock](../engine/stockfish.lock.json) pins the official source-containing sf_19 archive/full SHA, NNUE/full SHA, NDK, API26, ARM64/x86_64, 16KB alignment and reviewed output hashes. [Preparation](../scripts/prepare-stockfish.mjs) verifies downloads before extraction, clears inherited compiler flags, builds non-PGO PIE executables/static libc++, strips debug paths and rejects checksum drift. Generated files stay ignored in `.engine-cache/`. Gradle verifies, never downloads executable code. Linux-host checksum reproducibility/hosted CI need actual hosted validation; failures must not be suppressed.

APK assets include full GPL/AUTHORS, corresponding C++/build scripts (`engine/source.tar`), exact NNUE, lock, preparation recipe/patch and identity/checksums, plus NDK/LLVM notices. The plain tar avoids AAPT's transparent `.gz` filename/byte rewriting. C++ is unchanged; a disclosed GPL build-only [Makefile patch](../engine/disable-git.patch) removes upstream automatic Git checks/index refresh. A guard rejects unpatched shell-Git expressions before make. NNUE embedding is disabled at compilation: both executables share one supplied data asset, atomically verified/copied into private app storage. Retain all notices and exact source/network/build materials when distributing binaries; publication/signing remains owner-managed.

The `libstockfish.so` filename only permits extraction into Android's read-only native directory. Stockfish is executed, never loaded as an Ashva library. Writable-storage executable code would violate [Android restrictions](https://developer.android.com/about/versions/10/behavior-changes-10#execute-permission). Builds use [16KB ELF LOAD alignment](https://developer.android.com/guide/practices/page-sizes); the official universal ARM64 executable had 4KB alignment and is not packaged. Alignment is not physical-device/16KB/API37 runtime QA.

## Learner behavior

Verified Android0.14 also exposes this bounded engine in **Players & GM games**. It compares the next recorded move at the current original board, labels hypothetical continuations separately, disables original controls during preview and returns to the unchanged original game. Root validation retains complete declared history; immutable root/checked-transition reuse avoids reconstructing that history for each output line. No search/output ceiling or terminal-legality check is loosened. See [the game-coach contract](HISTORICAL_GAME_COACH.md).

Open a lesson → Full idea → **Analyze alternatives → Analyze this position**. Compare the next lesson move, or a legal off-line attempted move after one is made. Both colors work; lesson position/cursor/hints/policies stay unchanged. **Explore this analyzed continuation** provides a separate hypothetical board with first/previous/next/last and return. Android 0.12 checks SAN/UCI again and explains each actual board change; its separate position-ideas panel distinguishes facts from conditional plans. Previews are ephemeral, not named theory, source commentary or independent expert review. See [grounded teaching](GROUNDED_TEACHING.md).

**Strongest found at this budget** is not proven best. The compared move gets a separate restricted-root search with identical limits and cleared hash, even when already in MultiPV. Actual depth/nodes/time, engine/build SHA, NNUE and perspective remain attached. Equal ceilings do not imply identical search effort/depth. Root-relative scores and bound direction flip explicitly for White/Black. Mate is not CP. The labeled 20-cp near-equality threshold is a heuristic, never a proof/blunder classifier.

Defaults per search: depth16 / 50,000 nodes / 1,000ms, three alternatives, one thread, 16MiB hash; original comparison is a second search. Accepted maxima: depth30 / 200,000 nodes / 3,000ms / five candidates / 32MiB hash. UCI stopping targets allow small overshoot; not battery guarantees. No pondering or automatic background analysis. Stop, position changes, playback restart, screen disposal/background and ViewModel clearing cancel/terminate; generations reject stale results. Timeout becomes a retryable error, not perpetual Loading.

## Validation and limits

Every scored PV legally replays to SAN. Invalid/post-terminal moves, duplicate candidates, bad metrics/score/identity/network, EOF or oversized output fail closed. Depths are not mixed; missing candidates stay partial. Bounds/mates/missing scores are not exact CP comparisons. Cache identity retains initial FEN/clocks, complete ordered history, original move, engine/NNUE, limits and protocol version—not just a transposition key. FEN starts disclose unknown earlier repetition. Shared rules handle terminal states without search, retaining conservative dead-position limits.

Supported engine ABIs: ARM64/x86_64; others report unavailable. NNUE increases APK/process memory; representative-device battery/thermal/frame/memory tests and App Bundle/store readiness remain release work. Shared analysis compiles for iOS; its adapter/client is later. No permanent analysis cache, automatic prose/GM intention, mastery, complete theory or guaranteed wins is implied. Exact gate/device outcomes are in Relay history; see [user testing](USER_TESTING.md).

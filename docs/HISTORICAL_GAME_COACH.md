<!-- SPDX-License-Identifier: Apache-2.0 -->
# Original-game coaching

Android 0.14 adds move coaching to **Players & GM games**. Complete gates pass:162 JVM/128 Android-host/24 importer/all56 isolated native cases, builds/lint and both shared iOS compilations. Relay0031 records final acceptance;0029–0030 retain the earlier checkpoints/failure. This is finite offline game coaching, not independent expert annotations or completion of the broader roadmap.

## What the learner sees

The recorded original mainline/result is the default. Next, Previous, a move-list jump or original autoplay prepares the last played move’s **ASHVA BOARD FACT**: what actually changed, the moving color, and an original conditional principle. Castling, captures, promotion, development, central pressure and attacked pieces come from the checked board, not a guessed player intention. **Understand this position** separately explains both-color facts and conditional middlegame/endgame plans. These are not independent expert annotations or a proof that a move wins.

Source games retain their exact pack/hash/record; private PGNs retain their local canonical record. User comments are labeled unverified and never converted into Ashva’s explanation. Original annotations/recursive variations remain stored; this screen teaches the original mainline, not a claim that every supplied variation has reviewed commentary.

## Alternatives and divergence

**Analyze this position** pauses original autoplay. The root is the current board, **before the next recorded original move**. To compare a move just played, go Previous first. The next original move receives a separate restricted-root search, alongside bounded engine candidates. At the score’s endpoint there is no invented next original move or replacement result. Earlier repetitions from a declared PGN start are preserved; a custom-FEN start discloses unknown pre-FEN history.

**Strongest found at this budget** is not proven best. Scores have explicit White/Black perspective, depth/nodes/time/bounds/mate and engine/NNUE provenance. The20-cp near-equivalence threshold is a heuristic, not equality proof or a blunder classifier. Missing candidates remain partial. Every accepted continuation is legally replayed and its SAN rechecked before adding move explanations; wrong root/budget/compared move/terminal status or invalid notation fails closed.

**Explore this analyzed continuation** opens a visibly **HYPOTHETICAL ENGINE LINE** with first/previous/next/last and checked move explanations. The original board/cursor stays where it was; original navigation/autoplay is disabled during the preview. **Flip POV** changes perspective, not the root or source moves. **Return to unchanged original game** restores the original context exactly and does not resume playback without a deliberate Play tap. No preview changes the source result, opening selections or recall mastery.

## Offline, caching and bounds

Teaching and the separate Stockfish process work offline without an account, remote model or per-move network call. One move explanation is prepared on a cancellable worker; a bounded128-entry memory cache is keyed by exact content fingerprint, explanation version and original ply. Cold resume recomputes facts offline from the retained original. This is not a persistent engine cache. Analysis is user-initiated, not automatic pondering.

Engine history is bounded to512 original half-moves. Beyond that, full private replay/teaching still works (private import limit4096), but the UI explicitly reports unavailable analysis rather than silently dropping repetitions or reducing the game to an isolated FEN. Navigation, backgrounding, position changes, Stop and ViewModel disposal cancel analysis; generation/source/cursor checks reject late results. Timeouts/unavailable engines are retryable errors, not fake recommendations or perpetual Loading.

See [the game library](GM_LIBRARY.md), [engine distribution and search limits](ENGINE_ANALYSIS.md), [grounded explanations](GROUNDED_TEACHING.md) and [testing walkthrough](USER_TESTING.md). Private imports are not added to licensed/public observed populations. Cloud service, persistent recall, board/device performance and the playable iOS client remain separate work.

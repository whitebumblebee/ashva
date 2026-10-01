# Opening Lab — branching learning design

This specification records the user's 2026-09-30 discussion and explicit follow-up choice. It is a target design, not an assertion that the prototype already implements it. `.relay/PROJECT.md` contains invariants; `.relay/tasks.md` owns task state and order. This document is not another task tracker.

## The experience

The learner chooses an opening or enters the opponent's moves/position, then chooses White or Black. The app identifies the opening where possible, offers its documented branches, and teaches both the move sequence and why it works. Recognition may return multiple names through transpositions or report unknown; opening names do not dictate whether a move is good.

### Study a line

Provide a **Full idea** action: show the complete selected line as a scrollable move list and an overview of its goal, pawn structure, piece placement, breaks, threats, tactical traps, common mistakes, opponent counterplay, and relevant middlegame/endgame themes. Both players' moves stay visible; the chosen color controls board orientation and explanatory emphasis, not whether the other side's moves disappear. One board displays one position at a time; showing all moves means revealing the entire sequence, not overlaying every position simultaneously.

The replay player has first, previous, next, last, jump-to-move, autoplay, and pause. Each step explains the current move's purpose, the opponent's threat/reply, and useful alternatives where supported. Autoplay must pause for a branch decision and respect the user's speed and cancellation. A branch card offers its name, short idea, and Stay on this line / Explore this variation. Exploring retains a return point and permits nested branches without losing the original route.

Example: after `1. e4 e5 2. Nf3 Nc6 3. Bb5`, the learner can stay with `3...a6` (Morphy Defence) or explore `3...Nf6` (Berlin Defence). Explain each from the selected perspective, replay its continuation, then return to the choice point or original lesson. Switching a study branch need not automatically replace a saved repertoire preference.

### Practice a line

The user explicitly selected: **keep the lesson position and show the correct move; offer a branch switch when available**.

- An expected legal move advances the active lesson path. Opponent replies follow that path, except that a pending branch decision pauses automatic advancement.
- An incorrect attempt immediately shows the expected move in notation and as a board highlight/arrow. No additional Hint-button tap is required. The board remains at the lesson position.
- An illegal attempt stays blocked with a concise reason and the same immediate hint; never permit illegal moves for training purposes.
- A legal off-line move is described as different from this lesson, not necessarily bad. If it matches an available branch, offer its name/idea and an explicit switch. Only confirmation changes the active path and applies that move.
- An uncovered move remains off the lesson path and is labeled uncovered, not disproved. Later engine analysis can offer bounded exploration without claiming a named/theoretical variation where none is sourced.
- Manual hints remain available. Assisted attempts are separate from unaided recall, and viewing a line is not evidence of mastery.

State must survive replay jumps, branch return, board flip, screen recreation, and relaunch once persistence is implemented. Switching chosen color affects perspective, not the legal move history or canonical position.

## Full historical games

Add a dedicated GM library with search/add/follow and downloadable player/game collections. Support aliases and reliable player identities; provide opening, event, year, result, and color filters. Let the user add selected GMs over time or import PGN, rather than requiring all GMs at installation. A curated famous-games shelf must have an explicit selection rationale and verified records. The first roster is not chosen yet.

On opening a game, replay the actual complete game in its recorded order by default. At each move, provide its idea and, where useful, plausible alternatives and engine-supported continuations. Show the original move, source, and result distinctly from hypothetical analysis. Offer Explore alternative / Stay with the game and Return to original game. Branching never modifies the source PGN or falsely attributes a generated line to the GM.

Analyze the original move as well as alternative candidates at comparable budgets. Alternatives may be equally sound, easier to play, historically common, or the strongest found by the engine; not every original move has a demonstrably better replacement. Explain uncertainty and context. Do not claim knowledge of a player's private intention or quote publisher annotations without rights. If an annotation is missing, show that status instead of inventing historical commentary.

## What broad repertoire coverage means

A names list is not a repertoire, and a repertoire is not every legal continuation. The delivery target combines:

1. Documented opening names/positions and major/minor theoretical branches, including transpositions and systems reached through different move orders.
2. Game-observed replies and continuations, ranked by frequency in an explicitly labeled population (masters or an appropriate player pool). Observed results are not guarantees or calibrated forecasts.
3. Chosen repertoires: preferred learner moves plus coverage of meaningful opponent replies, with reasons for those choices. White and Black plans are authored separately where their objectives differ.
4. On-demand legal off-book exploration and bounded analyzed continuations, labeled generated rather than historical/theoretical.

No finite catalog can enumerate every possible move sequence, nor can an opening guarantee a win. Teach sound play, advantageous plans, tactical punishment, defenses, and conversion of winning positions. Publish imported families/variations, depth ranges, full game counts, source versions, samples, and gaps. Numerical release targets and storage budgets must be established after measuring candidate sources. The app should show what is downloaded, available remotely, and unsupported.

## Separate sources for separate jobs

| Need | Candidate | What it does not establish |
| --- | --- | --- |
| Opening names, canonical moves/positions, ECO labels | [Lichess chess-openings](https://github.com/lichess-org/chess-openings), CC0 | Not a full instructional repertoire or annotated game archive. |
| Master-position statistics and example games | [Lichess API](https://lichess.org/api), Masters explorer and individual PGN endpoint | Not every GM's career, exhaustive variations, or permission to redistribute every collection. |
| Modern tournament games | [Lichess broadcast exports](https://database.lichess.org/#broadcasts), CC BY-SA 4.0 | Not the site's standard-game CC0 license, and not all historical games. |
| Broad online games / optional precomputed evaluations | [Lichess database](https://database.lichess.org/) collections | Not automatically curated GM over-the-board games or ready-made lessons; verify each collection's terms. |
| Older/famous historical games and explanatory annotations | Additional explicitly permitted archives or user-supplied PGN | Source availability and annotation rights still need auditing. Import permission does not automatically grant publication rights. |
| Candidate moves and continuations | [Stockfish](https://stockfishchess.org/about/) and reproducible analysis | Not opening naming, natural-language teaching, a proof of winning, or original GM intention. |

The 2026-10-01 [content audit and pipeline](CONTENT_PIPELINE.md) implements a pinned CC0 taxonomy snapshot and one CC BY-SA broadcast month as **local packs**, not Android integration. Masters redistribution rights remain unresolved; no Masters requests, additional archives or evaluations were acquired. The inspected Masters spec lists `https://explorer.lichess.org/masters` and `https://explorer.lichess.org/masters/pgn/{gameId}` with advertised OAuth2 security; verify the [current API schema](https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/lichess-api.yaml) and access before integration. Explorer defaults to 12 popular moves and up to 15 example games: do not mistake a capped response for a complete tree. Cache and follow [provider rate-limit guidance](https://lichess.org/page/api-tips), including serial requests and handling 429s; do not assume a fixed free quota.

Preserve raw source identifiers, download/import date, upstream revision, collection license/attribution, and processing version. Validate every imported move against the rules implementation; quarantine malformed, duplicate/conflicting, or unsupported-variant records with clear reports. A Lichess account's game history must not be presented as the GM's entire over-the-board career. Never infer a dataset license from an API server's code license.

## Local database and optional service

**Use a local database.** Recommended: [Room KMP](https://developer.android.com/kotlin/multiplatform/room) with bundled SQLite behind shared repositories. Persist downloadable content, selected repertoires, saved positions/branches, bookmarks, attempts, and review history. Platform-specific database construction stays outside common logic. Design portable record/pack imports; Room's Android prepackaged-database helpers are not all available in common KMP.

**A server is optional for the first offline slice, recommended for the large evolving product.** A local importer can create versioned packs initially. A service becomes useful for centralized ingestion, huge archive search, cached expensive analysis, content updates, optional cloud explanations, and optional cross-device sync. Do not ship a huge whole-corpus mobile database or make every replay tap depend on a network/AI request.

| Boundary | Responsibility |
| --- | --- |
| Compose Android app | Board/input/animation, move list, perspective, lesson controls, accessible UI. |
| Shared KMP domain | Rules/notation, identification, lesson graph and paths, replay/branches, repository and engine contracts. |
| Local Room/SQLite | Offline lesson/game packs, local position index, cached analysis, user learning state. |
| Optional API + PostgreSQL | Searchable metadata/identities, content index/versions, analysis cache index, optional user sync. |
| Object storage / pack delivery | Original permitted PGNs, source snapshots, versioned downloadable lessons/games; device receives selected packs. |
| Background workers | Import/deduplication/legal validation, statistics, bounded engine analysis, reviewed annotation production. |

Kotlin/Ktor is a candidate API stack to retain Kotlin familiarity, not an installed service. Use queued jobs and deduplicated analysis rather than blocking request handlers for deep engine work. Cache by content/analysis version, honor upstream throttling, bound CPU and model cost, retry safely, and keep credentials server-side. Deployment/provider/accounts/budget and sync authentication remain undecided; this plan authorizes no paid setup or publication.

### Data contracts

Suggested models, to validate during the graph/store work:

- `Source` / `ContentManifest`: upstream ID, revision/date, license/attribution, validation status, counts, and pack checksum/version.
- `Position`: normalized pieces/side/castling rights/legal en-passant availability for transposition identity. Maintain halfmove counters and repetition history separately when evaluating draws; a position key alone is not sufficient game state.
- `MoveEdge`: legal UCI move, position transition, computed SAN, source-population statistics, and links to annotations. A graph may cycle; traversals and generated continuations must be bounded.
- `OpeningTag` / `LessonPath`: family/name/variation labels, ordered path, branch choices, perspective-specific ideas, source, and depth/coverage. The same position may occur in multiple named and pedagogical paths.
- `Player` / `Game` / `GamePly`: identity/aliases, event/date/result/source and immutable original move order. Optional annotations and hypothetical branches do not replace this sequence.
- `Analysis`: position plus engine/NNUE version and search settings, resource budget, candidate scores/PVs, perspective, completion/error status, and creation date. Game-history context that affects draws must not be ignored in cache identity.
- `Annotation`: source or author, reviewed/generated status, links to move/lesson/analysis evidence, selected-perspective narrative, and confidence/limitations.
- `UserRepertoire` / `Attempt` / `Bookmark` / `Review`: chosen paths/preferences, assisted/unaided outcomes, saved cursor/branch state, and real scheduled review data.

Keep user state separate from replaceable content. Validate/checksum packs and install atomically so interruptions cannot erase progress or leave half-installed lessons. Support schema/content migrations and unknown/newer content versions with safe errors.

## Analysis is not the teacher by itself

An engine supplies evaluations and candidate continuations, not complete strategic prose. [Stockfish's developer guidance](https://official-stockfish.github.io/docs/stockfish-wiki/Developers.html) explicitly leaves commentary to the application. MultiPV provides alternative lines but increases computation; score differences at a finite search budget are not absolute proof. Record resource settings, evaluate the played move, normalize scores for the chosen perspective, handle mate scores, and verify every continuation.

Use permitted curated opening knowledge and structured plan/motif templates as the teaching foundation. Optional model-generated prose can explain these facts more naturally, but must be grounded in legal positions, checked lines, analysis, and sourced knowledge. Do not let a language model invent the move authority, pretend to retrieve missing games, or produce unverified tactical claims. Define review/evaluation examples for both colors and for uncertain positions. Reuse cached explanations rather than generating the same lesson at every visit.

An Android engine adapter and later iOS adapter need feasibility, resource, and distribution checks. [Stockfish is GPLv3](https://stockfishchess.org/about/); verify the specific bundling/linking/distribution approach and obligations before releasing binaries. No engine has been selected or bundled into this app yet. A server fallback is possible, but must expose connectivity/cost limitations and must not be assumed to solve every licensing decision.

## Compose and verification requirements

Keep the Android experience native Compose, including the board, with Material 3/adaptive layouts and the existing Navigation 3 direction. Use current supported compatible library releases and central version management; review official release notes before upgrades. No XML screens, Fragment/View UI, or WebView-rendered board. Android manifest/theme/icon XML is still a normal platform resource requirement.

Follow [Android architecture guidance](https://developer.android.com/topic/architecture/recommendations) with immutable UI state, a clear repository boundary, lifecycle-aware collection, constructor injection, and asynchronous domain/data work. The shared module must remain Android-independent; iOS UI choice comes later rather than being silently locked in now.

Verify actual interaction and smoothness: legal move handling, both orientations, full move list/replay, autoplay cancellation, hints appearing automatically, branch prompts and return, accessible controls, restored state, and offline/missing-data behavior. Add unit/property/reference rules tests, replay/import/analysis contract tests, Compose UI tests, and representative-device frame benchmarks. Screenshots alone do not establish chess.com/Lichess-like responsiveness.

## Current scope and next action

The discussion turn changed only this design and Relay planning. Subsequent work added legal rules/notation, identification, PGN/FEN import, graphs, Study/Practice, Full idea, both-color plans, replay, hints and branches. Licensed ingestion produced 3,815 taxonomy sequences and 79 archived mainlines. Android v0.3.0 now bundles/install these into Room KMP/SQLite with exact dependencies, atomic updates, rollback and durable learner bookmarks/attempts/selections. The Continue card resumes cold-relaunch seed context; review statistics/scheduling remain samples. Source data is not yet broader teaching or a verified GM shelf. No engine/server/paid integration/Git or iOS client exists. See [pipeline limits](CONTENT_PIPELINE.md), [offline storage](OFFLINE_STORAGE.md) and Relay for evidence.

The next task is `repertoire-coverage`: connect installed taxonomy/routes to searchable sourced repertoire coverage, preserve source/depth limits and keep unreviewed routes distinct from authored teaching. Engine-grounded explanations and GM teaching follow their dependencies. The Ruy López seed demonstrates interactions, not full opening theory or analyzed winning recommendations.

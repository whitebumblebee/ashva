# Deep opening courses and GM-game teaching — implementation plan

Status: **pilot implemented in Android 0.16.0/code17** (Relay task `deep-course-pilot`, 2026-10-05). See [Pilot implementation](#pilot-implementation-016) for what was built, the measured numbers and the deviations from this plan. Scaling to further chapters/openings is the next step.

This document is written so any agent (Codex, Claude Code, Cursor, …) can implement it phase by phase. Do the phases **in order**. Each phase lists what to build, what it produces, and the gate that must pass before moving on. Record every phase in Relay history like any other task.

---

## 1. The goal in one page

Ashva should teach openings the way a strong YouTube/course author does (for example a full Ruy Lopez repertoire series aimed at 1600 → 2100+), but on an interactive board, with more branches and more depth. It should also teach famous GM games the same way.

**Track A — repertoire courses**

- A course = one opening from one side (e.g. *Ruy Lopez for White*), split into **chapters** by the opponent's main choices (Berlin, Marshall, Breyer, Open, Schliemann, early deviations…).
- Each chapter has a **main line** and **branches for every relevant deviation by either side**, including bad-but-common moves at club level and how to punish them.
- Every line is played until it **resolves**: one side is clearly better, or the position is equal and the plan is understood. That is where course authors stop, and so do we.
- Every move carries an explanation: why it is played, what it prepares or prevents, what the opponent wants, typical mistakes and traps.
- Study mode walks through it; Practice drills the learner's moves; the existing recall scheduler reviews them.

**Track B — GM games**

- The real game is the main line, explained from **both White's and Black's point of view**.
- At critical moments there are branches: "better was…", "if he had played this, it goes like this…". Each branch is also played to a verdict.
- The original game is never rewritten (existing invariant).

**What "all variations" means (finite and measurable)**

At every opponent move inside a chapter we include every move that is *relevant*, defined as any of:

1. common at the target level (club games),
2. played in master/GM practice (theory),
3. strong according to the engine,
4. a tempting mistake (common but losing) — included as a "punish it" line.

At the learner's own moves we teach **one recommended move** (optionally one alternative). Lines stop at an engine-checked verdict, or when the position becomes too rare to matter (reach probability below a cutoff). Moves outside the course are still handled live by the on-device engine. The default thresholds are in Phase 5 and are tunable per course.

**Who writes the explanations:** an AI model (no human reviewer is available). To make that trustworthy, the model never supplies chess facts from memory. Moves come from game data, evaluations from Stockfish, and every claim the model makes is machine-checked before it is shown. Content is labelled in the app (see §2).

---

## 2. Ground rules every phase must follow

- **Moves and evaluations come from data and the engine, never from model memory.** The model chooses what to teach and writes prose from verified facts only.
- **Every published explanation is built from checked claims** (Phase 7). Unverifiable claims are removed or labelled as weaker evidence.
- **Labels shown in the app:** `engine-checked`, `from game statistics` (weaker evidence), `generated` (all AI prose). Never present generated text as a human author's or a GM's words.
- **No copying** of books, paid courses or videos (including the YouTube series used as inspiration). They may be watched privately as a quality benchmark only. Course text must be original.
- **Data rights:** every new source goes into `content/sources.json` with license evidence, exactly as `docs/CONTENT_PIPELINE.md` already requires. Keep CC0 and CC BY-SA data separate.
- **Existing invariants still apply** (`.relay/PROJECT.md`): Compose-only UI, shared rules in KMP `shared`, additive Room migrations only, package/database identifiers unchanged, learner data preserved, connected tests only on an isolated emulator, all Git work owner-only, no paid service without owner authorization.
- **Determinism:** every build step is a re-runnable CLI with pinned inputs and versioned outputs. AI output is cached by input hash, so re-running does not silently change published text.

---

## Phase 0 — Owner decisions and setup

Ask the owner (record answers in `.relay/PROJECT.md`):

| # | Decision | Default if the owner has no preference |
|---|---|---|
| D1 | Writer model/provider (e.g. Claude or GPT via API, or a coding agent writing in sessions) and monthly budget | Run the bake-off in Phase 9 first; use in-session agent writing until then |
| D2 | Personal use only, or public release? (decides which game sources are allowed) | Personal use; public release needs a separate rights review |
| D3 | Target learner level and the game rating band that defines "club level" | Learner 1600 → 2100; Lichess rapid/classical games rated 1600–2200 |
| D4 | Pilot course and chapter | Ruy Lopez for White, chapter: Berlin Defence |
| D5 | Pilot famous game(s) | Owner picks one Ruy Lopez game; otherwise choose a well-known decisive Ruy Lopez game from an approved source |

Setup:

- Claim `deep-course-pilot` in Relay.
- Confirm the host Stockfish build works (`node scripts/prepare-service-engine.mjs`, see `docs/CONTENT_SERVICE.md`).
- Create a new JVM module **`courseTools`** (same pattern as `contentTools`: reuses `shared` legal rules, no second rules implementation). All build-time course tooling lives there. Output goes under `content/courses/`.

**Done when:** decisions recorded, module compiles, `./gradlew :courseTools:test` runs (even if empty).

---

## Phase 1 — Course pack format

Define the data format before any content exists. Put the model in `shared` (`commonMain`, so Android and iOS can read it) and its validator next to it.

A course pack contains:

- **Manifest:** course id, version, side (White/Black), target level, sources and licenses used, engine identity and budgets, writer model identity, generation date, label policy.
- **Chapters:** id, title, root move sequence, short intro (what the opponent chose and what the chapter teaches).
- **Line tree** (per chapter): nodes keyed by position, edges = moves. Each edge has:
  - `role`: `main`, `side`, `deviation` (opponent alternative), `trap`, `punish` (refutes an opponent mistake), `avoid` (a tempting learner mistake, shown but not drilled)
  - evidence: club frequency and score, master game count and score, engine eval/depth/PV (references into Phase 2–3 outputs)
  - `claims`: list of checked claims (Phase 7 format)
  - `text`: rendered explanation (short title + 1–4 sentences), with label
- **Leaf verdicts:** `white-better`, `black-better`, `equal`, `unclear-but-playable`, plus a 2–4 sentence "plan from here" for both sides and the evidence used.
- **Glossary references:** ids of reusable principle entries (Phase 6), so the app can link "see: the Nbd2–f1–g3 manoeuvre".

Map onto existing app structures rather than inventing a parallel player:

- A chapter becomes a `LessonGraph`; the main line and each branch become `LessonPath`s (`origin` = branch point). Add one additive enum value `LessonPathKind.COURSE_LINE` (and keep `ORIGINAL_GAME` / `ANALYZED_VARIATION` for Track B).
- Per-move text fills `LessonAnnotation` (`title`, `explanation`, `principle`). Add optional fields for label and verdict only if needed, with defaults so old data still loads.

**Done when:** schema documented in this file's appendix, shared validator rejects illegal moves, unknown roles, missing labels and broken references; unit tests cover one hand-made tiny course (5–10 moves) end to end into a `LessonGraph`.

---

## Phase 2 — Game data at club and master level

Build two **position statistics indexes**: position → move → games played, White/draw/Black results, average rating.

1. **Club index:** Lichess standard rated games (published monthly at database.lichess.org; verify and record the CC0 license in `sources.json`). Files are very large (tens of GB compressed per month), so:
   - stream-decompress and parse; never write the full PGN to disk
   - keep only games in the D3 rating band and rapid/classical time controls
   - keep only the first 30 full moves of each game (enough for openings)
   - start with 1–3 months; measure; add more months only if branches are too thin
2. **Master index:** Lichess broadcast games (CC BY-SA, already approved), extended from the current two 2020 months to all available months, filtered to strong players (e.g. both rated 2400+). If D2 = personal use, the owner may also import other PGN collections they legally have; mark those `private` so they never ship publicly.
3. **Evaluation cache:** the Lichess evaluation database (verify CC0) gives ready-made Stockfish evaluations with PVs for many positions. Import it as a lookup table keyed by normalized position, so Phase 3 only computes what is missing.

Implementation notes:

- Reuse `shared` PGN parsing and the normalized position key (transpositions must merge).
- Store indexes in a local SQLite or PostgreSQL database (the existing `contentService` PostgreSQL setup is fine). This is build-time data, not shipped to the phone.
- Record exact inputs (file names, checksums, filters) in a manifest.

**Done when:** for the pilot chapter's root position, the tool prints every recorded move with counts and scores for both indexes; a test proves transposed move orders merge; sizes and run times are written into this document.

---

## Phase 3 — Engine analysis toolkit

Build CLI commands (in `courseTools`, using the host Stockfish via standard UCI, as `contentService` already does). Each returns JSON and caches by (position + history + budget + engine identity):

| Command | What it answers |
|---|---|
| `eval` | Evaluation and best lines (MultiPV 5) at a deep budget (default: depth 28 or 15 s, whichever first). Uses the Phase 2 eval cache when deep enough. |
| `threat` | "What does the side that just moved threaten?" Let that side move again (null move) and search. Returns the threat move and the material/eval it would win. |
| `prevents` | Compare the opponent's best options before and after a move: which good opponent move became bad or illegal. This is how "h3 stops …Bg4" gets proven. |
| `compare` | Evaluate a specific move against the best move from the same position (eval loss). Used for mistakes/traps. |
| `stable` | Is the evaluation stable? Same verdict at two depths, and no pending captures/checks (quiet position). |
| `verdict` | Classify a position: `white-better` / `black-better` if the eval is at least +1.00 / −1.00 and stable; `equal` if within ±0.35 and quiet; otherwise `continue`. |

**Done when:** each command has tests on known positions, e.g. a fork position where `threat` finds the fork, and the Closed Ruy after 9.h3 where `prevents` reports …Bg4. Results are cached and re-runs make no new engine calls.

---

## Phase 4 — Gold set and evaluations (build these before the writer)

Without a human reviewer, measurement replaces review. Build the test set first so every later choice is measured.

1. **Tactics set (free):** sample ~300 puzzles from the Lichess puzzle database (CC0, includes theme tags such as fork, pin, mate, and opening tags). Test: given the position and engine facts, does the writer name the right tactic, and does the checker accept it?
2. **Opening gold set:** 100–200 positions from the pilot opening, covering quiet moves (like 9.h3), sharp lines, traps and punish lines. For each, store the key ideas as **machine-checkable facts** produced by Phase 3 (threats, prevented moves, eval swings) plus game statistics. An AI may draft candidate key ideas, but only engine-confirmed ones go into the gold set. The owner may skim for sanity but is not the judge of advanced theory.
3. **Metrics** (stored per run, compared over time):
   - claim pass rate: share of writer claims that pass the checker (higher is better; tracks hallucination)
   - key-idea recall: share of gold-set key ideas the writer mentions
   - teaching-quality score from an AI judge (a different model than the writer) using a written rubric: explains *why*, appropriate for 1600–2100, no padding, correct use of the provided facts. The judge rates prose quality only; chess truth comes from the checker.
   - owner rating on a small random sample (1–5) to calibrate the AI judge
4. **Regression:** every in-app "this explanation is wrong/unclear" flag (Phase 10) becomes a new test case.

**Done when:** an eval runner executes on a dummy writer and prints all metrics; the gold set files and their generation commands are committed under `content/courses/eval/` (by the owner, who manages Git).

---

## Phase 5 — Line tree builder (deterministic, no AI)

For each chapter, build the tree of moves **before writing any prose**. Start at the chapter root and expand:

**At the learner's moves:** choose one recommended move:

- candidates = engine moves within 0.30 of best **and** played in master or club practice
- prefer the move that scores best at the target level among those
- an owner override file (`content/courses/<course>/overrides.json`) can force or forbid moves
- optionally keep one alternative marked `side`

**At the opponent's moves:** include every move meeting **any** of:

- at least 3% of club games from this position (with at least 50 games)
- at least 5 master games
- engine top 3 and within 0.30 of best
- tempting mistake: at least 3% of club games **and** loses at least 1.50 → role `trap`, with a `punish` continuation

**Stop a line when:**

- `verdict` says `white-better`, `black-better` or `equal`, **and** the line is past the point where it has more than 50 master games or 500 club games (so main theory is not cut short), or
- the probability of reaching the position falls below 1 in 1,000 for this side's repertoire (the product of opponent move frequencies along the line), or
- a safety cap is hit (default 40 full moves); the cap is reported, never silent.

Every stopped line gets a verdict and the evidence for it.

**Outputs:** the tree with all evidence, plus a coverage report: number of lines, depth distribution, verdict distribution, the reach probability covered by the tree (target: at least 99% of club games reaching the chapter root stay inside the tree until a verdict), and every branch cut by a cap.

**Done when:** the pilot chapter tree is built, the coverage report meets the target or explains why not, and the owner has looked at the report (line count and depth) and agreed the scope looks right before any writing starts.

---

## Phase 6 — Retrieval: principles glossary and position lookup

The writer needs context beyond a single position.

1. **Structure classifier:** a deterministic pawn-structure key (pawn files and ranks for both sides, simplified), so positions with the same structure can be found across lines and games.
2. **Principles glossary:** an original, reusable set of entries (target 50–150 for the pilot opening): typical manoeuvres (e.g. Nbd2–f1–g3), pawn breaks (d4, …d5, f4/…f5), structures (Berlin endgame majority, Carlsbad, isolated d-pawn), typical tactical motifs. Each entry is written once, claim-checked where possible, and referenced by id from course text. The glossary is drafted by the writer from engine and game-statistics evidence, then evaluated like any other text.
3. **Retrieval tool** for the writer:
   - by exact position: club/master stats, engine facts, example games
   - by structure key: what winners did next in decisive games with this structure (move frequencies in won games vs lost games), and example games
   - by glossary: entries linked to this structure or move
   - by already-written course text: for consistency ("as in the Breyer chapter")
4. **Optional external text:** Wikibooks *Chess Opening Theory* is CC BY-SA. It may be retrieved as a hint with attribution, never copied wholesale, and its claims must still pass the checker.

**Done when:** for any pilot position the retrieval tool returns the stats, structure matches, top "what winners did next" moves and linked glossary entries in one JSON response.

---

## Phase 7 — Claim schema and checker

The writer outputs **typed claims with proof**, not free prose. Prose is rendered only from claims that pass.

| Claim type | Payload | Automatic check |
|---|---|---|
| `threat` | move(s) the side threatens, what it wins | `threat` command confirms the gain at budget |
| `prevents` | opponent move that is stopped | `prevents` confirms it was good before and is bad/illegal after |
| `prepares` | follow-up move or plan this move enables | follow-up is legal next turn and appears in the engine PV or in the club/master continuations |
| `mistake` / `trap` | the bad move and the punishing line | `compare` shows eval loss ≥ 1.00; punishing line is legal and matches the engine |
| `plan` | multi-move plan for a side | each move legal in sequence; plan moves appear in engine PVs or in "what winners did next" statistics → label `from game statistics` if only stats support it |
| `evaluation` | verdict wording ("White is better") | matches `verdict` at budget |
| `structure` | pawn-structure fact | computed directly from the board |
| any move/square mentioned | — | must be legal / must exist in the position |

The checker returns pass/fail with reasons. Failed claims go back to the writer once for repair; if they fail again they are dropped. Rendered prose may only use passed claims (a final pass checks that every move/square in the prose appears in a passed claim).

**Done when:** checker unit tests cover a passing and a failing example of every claim type; it runs on the Phase 4 gold set and reports results.

---

## Phase 8 — Writer pipeline

Make the writer **provider-agnostic**: define one interface "given this task + tool access, return JSON", with two implementations:

- **API mode:** a build script calls the D1 model with tool use (Phase 2/3/6 tools exposed as functions). Requires owner-approved key and budget; log cost per chapter.
- **Agent mode:** a coding agent (Codex, Claude Code…) follows a runbook in this repo, calls the same CLIs and writes the same JSON files. No API key needed; slower.

Passes, in order:

1. **Chapter outline:** from the Phase 5 tree and coverage report: chapter intro, which lines are main/side, which traps matter, the story of the chapter (what each side is fighting for).
2. **Per line, per move:** claims (Phase 7 schema) + a short title, using the retrieval tool. Learner moves explain *why this move*; opponent moves explain *what they want and how we answer*; `trap`/`punish` lines explain the mistake and the refutation.
3. **Leaf verdict text:** plan from here for both sides.
4. **Check:** run the Phase 7 checker; repair once; drop failures.
5. **Critic pass:** a second call (ideally a different model) with the same tools hunts for errors, missing key ideas (compare against gold-set style key facts for that position) and unclear wording. Its findings are themselves claims and must pass the checker.
6. **Revise and render:** final prose from passed claims only, in the style guide's voice.

Inputs the writer always receives: a **style guide** (`content/courses/STYLE.md`: audience 1600–2100, short sentences, always say *why*, name the opponent's idea, no hype, no "winning" without a checked verdict) and **5–10 example annotations** that the owner has approved as the target voice.

For hard positions, generate 2–3 drafts and keep only claims that agree across drafts and pass the checker.

All outputs are cached by input hash; re-running with unchanged inputs produces identical files.

**Done when:** the pipeline produces the full pilot chapter as a valid course pack (Phase 1 validator passes) and the eval runner reports metrics on it.

---

## Phase 9 — Model bake-off

Run the writer on the gold set and the pilot chapter with each candidate model (for example a GPT model and a Claude model), with identical tools, prompts and style guide. Compare:

- claim pass rate and key-idea recall (Phase 4)
- AI-judge teaching score, calibrated by owner ratings on ~20 samples
- cost and time per chapter

The owner picks the writer (and optionally a different critic). Record the choice and metrics in `.relay/PROJECT.md`.

**Done when:** a comparison table is written into this document and the owner has chosen.

---

## Phase 10 — App integration (Android)

1. **Install:** course packs install through the existing checksummed, atomic offline pack installer (`docs/OFFLINE_STORAGE.md`). Course versions are retained like other packs; bookmarks pin exact versions.
2. **Course browser:** Learn → course → chapters → lines. Show coverage facts honestly (number of lines, depth, verdict counts, the reach-probability coverage figure).
3. **Study:** existing Study/Full idea/replay/branch-return UI, now showing per-move title + explanation, the label (`generated · engine-checked` / `from game statistics`), branch roles (main / side / trap / punish) and the leaf verdict with "plan from here".
4. **Practice:** the learner plays their side; the app picks opponent replies **weighted by club frequency** so practice feels like real games; traps appear at their real rate. Existing automatic hints apply.
5. **Recall:** course lines become recall cards through the existing `review/Recall.kt` scopes (additive only; no change to existing schedules or card identities).
6. **Off-course moves:** if the learner (or a simulated opponent) leaves the course, say "not in this course" and offer the on-device engine with the existing template facts. Never call it wrong just for being off-course.
7. **Feedback:** a "wrong / unclear" button on every explanation stores the flag locally (new additive table) and can export a JSON file for the regeneration loop (Phase 12). No network upload.

**Done when:** the pilot chapter installs, studies, practises and reviews on the isolated emulator; new UI tests cover branch roles, labels, verdicts, weighted practice and feedback export; all existing gates in `.relay/PROJECT.md` pass.

---

## Phase 11 — GM-game track

1. **Game selection:** the owner's chosen games (D5) plus, later, a curated list per opening (decisive, instructive games from approved sources). Store the original score immutably, as today.
2. **Deep analysis:** run Phase 3 `eval` on every position of the game (deep budget, offline batch).
3. **Critical moments** (deterministic rules): eval swing of at least 1.00 in one move; an only-move (best move at least 1.00 better than the second); a missed win (best move wins and the played move does not); the first move after which the eventual loser never recovers; key plan moments where the played move matches "what winners did next" statistics.
4. **Branches** at each critical moment, each played to a `verdict`:
   - `better was`: the engine's improvement for the side that erred
   - `tempting alternative`: a natural move that fails, with the refutation
   - `if instead`: the opponent's best defence and how the game would continue
5. **Writing:** the same writer, checker and critic as Phase 8, writing two points of view per move (White's and Black's thinking) and a short story of the game (opening choice, turning point, how the win was converted). Historical moves are labelled as played; branches are labelled `engine line`; never claim to know what the player intended.
6. **App:** reuse the existing GM library replay and historical coach screens; branches become `ANALYZED_VARIATION` paths branching off the immutable `ORIGINAL_GAME` path; return-to-game works as today.

**Done when:** the pilot game plays through with both POVs, at least one branch per critical moment, all claims checked, and the original score unchanged (existing tests extended).

---

## Phase 12 — Pilot review, regeneration loop, then scale

1. Owner studies the pilot chapter and game for a few sessions, flags anything wrong or unclear, and compares privately against the inspiration course on the same chapter.
2. **Pilot passes** when:
   - claim pass rate at least 95% before dropping, and 100% of shown claims pass
   - key-idea recall at least 80% on the gold set
   - no unresolved owner flags on the main line
   - the owner judges the chapter teaches *why*, not just *what*
3. **Regeneration loop:** flags become gold-set cases; fix prompts, style guide, thresholds or tools; re-run; compare metrics; publish a new pack version (old versions retained for bookmarks).
4. **Scale:** remaining chapters of the pilot course → the other side of the opening → other popular openings (owner's order) → more GM games per opening. Re-run evals on every change. Publish a coverage report per course.

---

## Definition of done for one course chapter

- Tree built with the Phase 5 rules; coverage report published; every cap reported.
- Every line ends in a checked verdict with "plan from here" for both sides.
- Every shown explanation is built only from checker-passed claims and carries its label.
- Evals meet the Phase 12 thresholds; no unresolved owner flags on the main line.
- Pack validates, installs atomically, studies/practises/reviews in the app; all project gates pass.
- Sources, licenses, engine identity/budgets and writer model identity are recorded in the pack manifest.

## Suggested Relay task split

When the owner resumes development, split `deep-course-pilot` into tasks that match these phases (one owner per task, claim before editing):
`course-format` (1), `course-game-index` (2), `course-engine-tools` (3), `course-evals` (4), `course-tree-builder` (5), `course-retrieval` (6), `course-claim-checker` (7), `course-writer` (8), `course-model-bakeoff` (9), `course-app-integration` (10), `gm-game-teaching` (11), `course-pilot-review` (12).
Phases 2, 3 and 4 can run in parallel after Phase 1; everything else is sequential.

## Appendix — course pack schema

The authoritative schema is the `@Serializable` model in `shared/src/commonMain/kotlin/com/openinglab/shared/course/DeepCourse.kt` (`DeepCoursePack` → `CourseChapter` → `CourseNode` with role, title, text, principle, label, reach weight, evidence and leaf `Verdict`; game chapters add `CourseGame`). `DeepCourseValidator` lists every rule a pack must satisfy.

## Appendix — measurements and bake-off results

Pilot measurements (2026-10-05, this machine; network ~80 KB/s per connection):

| Item | Result |
| --- | --- |
| Club input | First 1 GiB of the September 2026 standard export: 3,284,678 games scanned → 8,905 Ruy Lopez games (both players 1600–2200, 5+0 or slower) → 1,272 reach the Berlin |
| Master input | 12 most recent broadcast months + the retained 2020-01/04 exports: 389,344 games → 2,001 Ruy Lopez games with both players 2400+ → 554 reach the Berlin |
| Berlin tree | 285 positions, 27 lines, 11–46 half-moves (median 22); verdicts: White better 4, equal 21, unclear 2; stops: verdict 19, reach cutoff 7, length cap 1; 4 common mistakes with punish lines; 80.2% of club games reaching the Berlin stay inside the tree until a verdict |
| Game chapter | Firouzja–Carlsen, Tata Steel 2020 (0-1): 78 original half-moves, 4 critical moments with engine branches, 94 positions |
| Engine work | Stockfish 19 host build, depth 22 (≤30 s per search), 3 processes × 4 threads; ≈4,000 cached searches for tree + facts |
| Claims | Writer (agent mode): 239 claims on 161 positions, all passing after repair; the checker rejected 9 first drafts (unsupported squares in general ideas, a false statistic, three "mistakes" below the 1.00 bar). Automatic: 232 of 233 passing (one 0.91-pawn "mistake" dropped). Positions without written claims show automatic engine/statistics claims or rules-derived board facts. |
| Pack | 379 positions, 32 lines, 190,867 bytes (`v1/course.json`, SHA-256 in the manifest and pinned in the app) |
| Puzzle evals (300, depth 16) | Engine finds the first solution move 100%; checker rejects false threat claims 100%; checker accepts the puzzle's real blunder as a mistake 98.3%; null-move threat probe picks the next solution move only 14.6% (a coarse tool, so threat claims are limited to forcing moves) |
| Gold set | 150 engine-confirmed key ideas; shown text mentions 99.3%. The gold set is derived from the same engine facts as the automatic claims, so this mainly shows rendering does not drop them, not writing quality |
| AI judge / bake-off | Not run (needs a second model through an owner-approved API) |

## Pilot implementation (0.16)

Phase 0 decisions (owner asked to proceed without stopping; defaults applied and recorded in `.relay/PROJECT.md`): **D1** writer = Claude Code in agent mode (no paid API); **D2** data limited to Lichess CC0 standard games and CC BY-SA broadcasts, so the result is redistributable with attribution; **D3** learner 1600 → 2100, club games 1600–2200; **D4** *Ruy Lopez for White*, chapter *Berlin Defence*; **D5** *Firouzja–Carlsen, Tata Steel 2020* (classical, decisive, from the already approved January 2020 broadcast export).

What exists, by phase:

| Phase | Implementation |
| --- | --- |
| 1 Format | `shared/.../course/DeepCourse.kt`: `DeepCoursePack` schema, fail-closed `DeepCourseValidator` (legal SAN/UCI replay in file order, roles, labels, verdict on every repertoire leaf, bounds), `DeepCourseCatalog` mapping chapters onto the existing `Opening`/`LessonGraph` player (`LessonPathKind.COURSE_LINE`; game chapters keep `ORIGINAL_GAME` + `ANALYZED_VARIATION`). `MoveStep`/`LessonAnnotation` gained an optional provenance `label`; unlabelled annotations keep their exact previous text so saved-lesson fingerprints do not change. |
| 2 Data | `scripts/fetch-course-data.mjs` (resumable ranged downloads, ≤3 connections, shared ≥60 s cooldown on HTTP 429 with Retry-After) → ignored `.course-cache/`. `courseTools` `filter-club` / `filter-master` stream zstd chunks through a PGN filter; `PositionIndex` legally replays every kept game (transpositions merged, illegal scores rejected whole). |
| 3 Engine | `BuildEngine` (persistent host Stockfish 19 over standard UCI, PVs re-validated with shared rules) and `EngineTools` (`eval`, null-move `threat`, `prevents`, `compare`, two-depth `verdict`) with a persistent request-keyed cache. |
| 4 Evals | `courseTools evals`: 300 sampled CC0 puzzles (engine solves the first move; threat probe finds the next solver move; checker rejects false threats; checker accepts real blunders) and an engine-derived opening gold set (key-idea recall of shown text). The AI-judge metric needs a second model through an approved API and was **not run**. |
| 5 Tree | `TreeBuilder`: rules exactly as above plus three refinements found while building — "equal" may end a line only ≥16 plies into the chapter once both sides have developed; an opponent move that hands the learner a clear advantage becomes a `TRAP` and is continued as a punish line for at least two learner moves; no line ends on an opponent move. Learner moves prefer what strong players actually play among engine-sound moves; results decide only between well-sampled moves. |
| 6 Retrieval | Statistics/engine/board facts per edge plus a small original glossary. Structure-key retrieval across games and Wikibooks retrieval were **not built** in the pilot. |
| 7 Claims | `Claims.kt`: typed claims (`THREAT`, `PREVENTS` with optional comparison move, `PREPARES`, `PLAN`, `MISTAKE`, `EVALUATION`, `STRUCTURE`, `STATISTIC`, `IDEA`) and a checker; any move/square token in a sentence must be backed by that claim's checked payload, and `IDEA` sentences may not name moves or squares at all. |
| 8 Writer | Agent mode: the digest (`courseTools context`) feeds the writer; writer claims live in `content/courses/<id>/claims/`; automatic claims come from facts; `courseTools check` records every pass/fail with its reason; `courseTools pack` renders text only from passed claims, labels it, validates and publishes `v<version>/course.json` + manifest. |
| 9 Bake-off | **Not run**: needs the owner's API key/budget decision for a second model. |
| 10 App | Learn → *Deep courses* cards; chapter screen (how it was made, coverage, sources/licences, role filters, line cards with verdicts and reach, glossary, feedback count/share); trainer shows provenance labels and *Wrong/Unclear* flags; *Practice a random line* picks lines by club reach and does not pause at branches; course lines use existing bookmarks, cold restore and recall. Course text ships as a checksum-pinned APK asset; flags are a small app-private JSON file shared only via the Android share sheet. |
| 11 Game | `GameChapterBuilder`: deep analysis of every original position, rule-based critical moments (loss ≥ 1.00, only-moves whose alternative fails), engine branches played to a verdict, two-point-of-view writer claims at key moments. |
| 12 Review | Owner manual testing is the next step (docs/USER_TESTING.md, *Test the deep course pilot*). |

## Full course implementation (0.17)

Owner direction after the pilot: a course like a strong YouTube repertoire series, but on the board. It covers the opening's variations under their real names (important unnamed GM lines included), how players at different levels win each variation for both sides, then deviations to the greatest depth the data supports. Pack: `content/courses/ruy-lopez/v1/course.json` (side `BOTH`, 7.5 MB, 11,190 positions, 898 lines, 10 repertoire chapters + the GM game).

| Step | Implementation (`courseTools`) |
| --- | --- |
| Data | All Lichess broadcast months (CC BY-SA), Ruy Lopez games with both players 2200+, split into bands 2600+ / 2400–2599 / 2200–2399; club games from the CC0 September 2026 export (1600–2200, 5+0 or slower). Names come from the CC0 Lichess opening list. |
| Variation map | `VariationMap.kt`. It keeps every master move with ≥10 games and ≥5% share where the position has ≥20 master games. It follows the main master move while ≥3 games remain, always keeps moves on a named route, and keeps frequent club moves. Transpositions are expanded once; the other move order ends with `transposesTo`. |
| Table of contents | `courseTools toc` → `map/toc.json` + `TOC.md`. Chapters are assigned by variation name first (move orders transpose), then by move prefix. Important unnamed lines (≥150 master games, ≥15% of the parent's games, at a real branch) read `Name · move`; colliding labels extend backwards (`Closed · 7...d6 8.c3`). |
| Lines | `FullCourseBuilder`: roles `MAIN` (most played), `SIDE`, `DEVIATION` (club-only), `TRAP` (≥1.50 loss), then `ENGINE` / `PUNISH` continuation at depth 18 until a two-depth verdict (punish lines ≥3 plies). Engine searches are capped at 30 s. |
| How games are won | `WinningIdeas`. For each variation with ≥20 master games, it compares the winners' moves (piece + destination, ignoring each side's first move, which is the sub-variation choice) with the other games. Only games that continue ≥20 plies past the variation start count, so quick draws do not distort the result. A pattern needs ≥8 wins, ≥25% of wins and +10 points. Example games prefer over-the-board events and higher ratings, and avoid repeating an ancestor variation's examples. |
| Writing | Claude Code wrote 182 variation introductions (`IDEA`) and 111 `WINPLAN` claims. A `WINPLAN` passes only if every move it names is among that side's distinctive winning moves for the variation. In the 10 repertoire chapters 303 of 304 writer claims pass; the single failure is an introduction for a variation below 20 games that has no anchor in the course. The GM game passes 33/33. All 1,317 automatic claims pass. |
| App | Both-colour POV toggle; **Variations · how games are won** cards (bands, results, introduction, how White/Black wins, replayable example games, study the variation); line search; master moves show the players; variation introductions appear on the anchor move. The pack is checksum-pinned in `BundledCourses`. |

Limits: node-by-node notes away from variation starts are mostly board descriptions plus engine/statistics facts. Win patterns are statistical and exist only where the data shows a clear signal. Broadcast data includes rapid/blitz events. The model bake-off and an independent judge are still not run.

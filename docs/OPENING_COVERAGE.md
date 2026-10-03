# Delivered opening coverage — Android 0.12.0

## Main-entry opening courses

Learn and Explore → All now open **149 opening courses**, presenting every **3,815 named source route** plus **78 original study continuations in 47 families**. The opening pack installs from reviewed local APK assets on first launch without a network request. Failed preparation/install is explicit, with Offline library retry; the 13 legacy starter routes remain under Starter, and raw source presentations under Sourced.

Ruy Lopez exposes **235 + 9** routes, Sicilian **391 + 7**, French **212 + 5**, Caro-Kann **110 + 4**, Italian **187 + 3**, and London System **4 + 4**. The authored routes are 15–33 half-moves; source routes remain 1–36. The exact authored inventory and move sequences are in [StudyRoutes.kt](../shared/src/commonMain/kotlin/com/openinglab/shared/data/StudyRoutes.kt). Every source route is preserved, including its stable record ID and exact moves.

Every route now has White/Black guidance and move-by-move **rules-derived observations**: development, captures/en-passant/castling/promotion, changes in central attacks, new pressure on pieces, check, open rook files and doubled pawns. Named branch focuses and endpoint pawn/development facts supplement the original [family guides](../shared/src/commonMain/kotlin/com/openinglab/shared/data/OpeningGuides.kt). General guidance is explicitly labeled for families without a specialized guide. Plans are conditional; geometric attacks are not proof of winning captures or blunders. This is original Ashva teaching, **not independently expert-reviewed opening theory or verified historical intent**.

Course IDs are separate versioned `course:v1:source:…` IDs. Legacy seeds and raw source lessons are unchanged, so their fingerprints/bookmarks/policies are not silently rebound. Authored continuations have separate stable IDs and authored path kind, and do not identify hypothetical positions as sourced opening endpoints. Generation is cancellable/off Main, with a constructor-local exact-history prefix trie; transposed routes do not substitute another repetition/move history.

This substantially expands the teaching player, but **does not complete full theoretical repertoire coverage**: broader representative game populations, deeper continuation coverage beyond these routes and expert/content review remain. Named multi-family selection/practice is described below. The original immutable snapshot inventory and historical capability notes follow.

Raw source sequences remain available under **Explore → Sourced**. All **3,815 routes / 3,174 names / 149 name-derived families** from the pinned Lichess CC0 taxonomy are searchable and selectable, with no path truncation. This raw presentation remains unchanged; it must not be confused with the new Ashva-guided course presentation above.

| Source family | Delivered routes | Depth in half-moves |
| --- | ---: | ---: |
| Ruy Lopez | 235 | 5–36 |
| Sicilian Defense | 391 | 2–27 |
| French Defense | 212 | 2–20 |
| Caro-Kann Defense | 110 | 2–24 |
| Nimzo-Indian Defense | 99 | 6–21 |
| London System | 4 | 5–9 |

These are representative family counts, not a list of all families. Explore enumerates the complete delivered catalog; each family shows its route count/depth and lists every named route. Search filters the visible results explicitly. Continuation choices are paginated in groups of twelve with Show more; all paths remain in the graph.

## What a source route can do

- White/Black POV, complete notation, deterministic first/previous/next/last and move jumps.
- Playback pauses for available branch choices; switch deliberately and return to the exact original path/position.
- Practice preserves the board after a wrong attempt and automatically highlights/names the expected move. Legal off-line moves are not labeled blunders.
- Cold resume restores the route/color/cursor/branch/hint using the unchanged local database contracts. Wait for Saved for offline resume before terminating.
- Position identification uses normalized source endpoints, including transpositions. An unnamed intermediate/current position may show a last-known name; that is not evidence its continuation is taught. FEN has no preceding move history.

Source move descriptions state recorded moves, not reviewed strategy. White/Black plans report unavailable. Engine ranking/generated lines are not source annotations: 0.7.0's separate analysis action produces legal bounded hypothetical continuations without editing routes/policies. It does not turn taxonomy into strategic teaching or supply guaranteed wins/GM intention.

## Provenance and limits

The source is [Lichess opening names](https://github.com/lichess-org/chess-openings), pinned to revision `c67912be581f0793dbaa776be5ccf111e01f88d9`. App presentation preserves source labels, record IDs, move order and CC0 attribution; British/American spelling and common accented-name aliases apply to search only. Family grouping is mechanically name-derived, not a reviewed course taxonomy. Distinct records can share a name or transpose to the same position without losing their separate routes.

The [immutable pack manifest](../content/packs/lichess-openings-c67912be581f-import-v1/manifest.json) and [all route records](../content/packs/lichess-openings-c67912be581f-import-v1/openings.jsonl) are the exact inventory. [Pipeline documentation](CONTENT_PIPELINE.md) records acquisition/rights/validation; [storage documentation](OFFLINE_STORAGE.md) records bounds, updates and retained versions. The original opening pack bytes remain unchanged. The separately reviewed January 2020 game snapshot below is a new immutable pack, not an edit to legacy packs.

This is the full **delivered snapshot**, not full repertoires, exhaustive theory or all popular historical replies. The retained April fixture and separately added January game archive supply observed-move counts, not a representative master-population book or reviewed annotations. GM browsing/coaching remains separate work.

## Wider observed sample in 0.11.0

Offline library additionally offers the whole January 2020 broadcast snapshot: **952 inputs attempted, 857 accepted, 0 duplicates, 95 quarantined** (55 unfinished results and 40 invalid records). Accepted scores contain **75,073 half-moves**, 1–253 per score, with **66,046 normalized positions** and 857 last-known taxonomy matches. Every raw frame retains an ordinal/hash and one disposition. Source-reported events include Tata Steel, Gibraltar and the Women's World Championship; that is not independent game/player authenticity verification.

Install January and April packs for **936 accepted score records / 82,679 half-moves** under the existing bounded, first-visit observed index. Counts reflect only installed accepted records, never the 95 rejected inputs. The January manifest pins the exact retained opening dependency; original source/course IDs, moves, policies and bookmarks are unchanged. Neither archive expands theoretical lesson routes automatically or implies popularity across all masters. Full original-game learning is still a separate upcoming task.

## Original observed-reply slice in 0.6.0

Install both existing packs, then open **Build / edit my repertoire**. At each board position the editor shows how many of the **79 April 2020 score records** reach it, have a recorded reply, or end there. Each selectable source move shows its count; other observed moves are visibly outside this family snapshot and cannot silently become teaching branches. Counts split between selected, unselected and outside-snapshot moves for either learner color. This describes one position, not whole-repertoire coverage.

Counting uses the **first visit per stable score ID per normalized position**. Transpositions combine; later repeated visits do not add votes or substitute a different reply. Scores ending on that first visit are excluded from the reply denominator. Matching IDs across installed packs count once when their scores agree; conflicting scores reject the index. Different IDs are not guessed duplicate real games or verified GM identities. Zero observations, no installed sample, loading and read/validation error are distinct states. No count is an engine evaluation, win probability, historical intention or popularity claim beyond this fixture.

The editor retains and displays exact pack IDs, manifest checksums, upstream revisions, license/attribution, modifications and dependency hashes. Aggregation is local and cancellable on a worker; only the current installed broadcast versions contribute. No new dataset, engine, server or schema migration was added. Broader population rights/scope remain unresolved; the 79 records are not a substitute for representative master statistics.

## Personalized choices in 0.5.0

The trainer's **Build / edit my repertoire** opens a board editor, initially seeded from the selected recorded route. Choose one preferred move at each learner position and include opponent replies separately. White/Black policies have different identities. Browse another route and explicitly confirm **Add this route to my choices** to include its replies and replace conflicting learner preferences. Casual study/branch switches do not change these choices.

The editor reports fitting recorded routes, unanswered admitted prefixes, intentionally excluded known replies and source endpoints—not a percentage of all theory. Click a gap to inspect its board and choose a response. The current board also counts legal moves absent from this family snapshot. Selecting a reply can reveal a missing learner response; a source continuation absent in a particular original move order is reported rather than invented through transposition. Original records are never rewritten or silently dropped.

**Practice my repertoire** filters the lesson graph to original routes that fit the policy, starting with the longest fitting recorded route; the broad explorer remains unchanged. Only included branches are offered in policy practice. A different legal move still leaves the board unchanged and receives a hint, not a blunder label. Color is fixed in policy practice; create/edit the other color's policy separately. Source endpoints are not complete opening theory or the end of a game.

Room stores immutable policy revisions separately from the legacy active-route selection and bookmarks. Editing a policy does not invalidate a bookmarked older revision. Policies bind to a moves-only snapshot hash; changed/missing source versions retain saved choices and report unavailable instead of silently migrating them. Save confirmation matters before terminating the app.

## Combined saved-family overview in 0.8.0

**Home → My repertoires** shows a separate White/Black live overview. It validates each current saved policy against its exact move snapshot, unions reached normalized positions/included replies, and exposes conflicting learner preferences with both origins. A conflict link opens that family's exact position even if its editor previously remembered another cursor. Overview actions do not save preferences or rewrite source routes. Some opening families deliberately prescribe incompatible first moves; they may stay independent rather than being silently merged.

Counts include only admitted recorded prefixes, not dormant choices after an excluded reply. Member fitting-route counts may overlap across families and are not summed as unique games/theory. Missing/changed families stay listed with exact saved identity and no invented coverage; loading/error/retry remain explicit. The computation is cancellable/off Main, does not retain every graph, and fails explicitly beyond 256 members or 100,000 combined reached-position/reply entries rather than dropping them. The view rebuilds from existing immutable Room revisions after cold launch; no migration or extra learner writes.

The 0.8.0 overview remains read-only; the 0.10.0 feature below adds explicit membership and unified route-queue practice. Remaining coverage work includes popular-game reply populations and deeper continuation/content review. GM coaching remains upcoming. Course ordering is not an owner-approval blocker; the owner requested continued work across openings and then the remaining tracker tasks. The authoritative task order is [.relay/tasks.md](../.relay/tasks.md).

## Named multi-opening repertoires in 0.10.0

In **My repertoires**, select a color and create a named repertoire from saved family policies. Each member pins an exact policy ID/revision. Saving edited membership deliberately adopts the displayed current revisions; older sets and family revisions remain available to bookmarks. The additive Room 3→4 migration keeps earlier content and learner rows intact.

**Check set** validates every member and shared-position preference. Unavailable/changed members and conflicting preferred moves prevent unified practice; families are never silently omitted or given an automatic winning choice. Intentionally different first moves can remain separate named sets. A member with no fitting route also blocks practice until its choices are filled. The existing editor provides conflict links; edit current family choices, then explicitly save updated set membership.

**Practice this repertoire** interleaves all admitted route entries across members, using original family paths and policy-filtered branches rather than stitching new move orders. Previous/Next route, Study/Practice, hints and branch return retain a fixed learner color. Queue navigation is not demonstrated recall/mastery; overlapping named routes are entries, not unique games. Cold resume rebuilds the exact pinned set revision/queue and restores its index, lesson, branch/cursor and color even after newer member/set revisions exist.

Local bounds are 64 members, 10,000 queue entries and the existing overview's 100,000 reached-position/reply entries. Oversized/invalid sets fail explicitly without truncation. Graphs are processed one at a time on cancellable workers; one checked plan is retained. No source bytes, package/database identifiers, old policies or old set revisions are rewritten.

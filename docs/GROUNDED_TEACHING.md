# Grounded teaching — Android 0.12

Ashva supplements retained family/branch guides with **Understand this position** during Full idea replay. It works for every legal lesson position and both perspectives, including families without a specialized guide, and on a separate hypothetical engine-preview board. No server, account or language-model provider is required.

## What it teaches

The shared [position teacher](../shared/src/commonMain/kotlin/com/openinglab/shared/lesson/PositionTeaching.kt) produces an exact-position report, version `ashva-position-teaching/1`, containing your side and the opponent's counterplay. Points distinguish **BOARD FACT** from **CONDITIONAL PLAN**:

- Material inventory, pawn squares, doubled files, isolated/passed pawns, diagonal pawn links and blocked central pawns. Passed means no opposing pawn ahead on the same or adjacent file, not a winning-promotion claim.
- Open/half-open files, starting-square occupancy, king squares/check and castling rights. Occupancy does not prove a piece never moved, and rights do not imply castling is legal or safe.
- Attacked pieces with geometric attacker/defender squares. Pinned pieces still attack geometrically; counts do not establish hanging pieces, legal captures or winning exchanges.
- Multiple-attack patterns and king-ray pins, with exact squares and calculation limits. A pinned piece may capture or move along the ray; it is not necessarily immobile or lost.
- Original conditional plans for pawn support/blockade, opening files, development, rook coordination and king safety. Reduced-material suggestions compare king activity, pawn races, simplification and stalemate, not opposition/zugzwang/tablebase proof.
- Legal **current-turn** central pawn-contact candidates, with canonical SAN/UCI and enemy pawn squares. These are c–f-file advances/captures that immediately attack or capture an opposing pawn, not a complete break planner, engine ranking or forced line. The other color never receives a fabricated turn. No candidate does not mean no useful plan or preparable break exists.

Development/reduced-material/coordination lenses are explicit teaching heuristics, not phase classification or evaluation. Check and rule-terminal positions override those lenses; terminal boards produce no move candidates. FEN repetition limits and conservative dead-position detection remain.

## Engine continuations

Stockfish's legal candidates and budgeted scores remain separate. The worker checks each returned SAN/UCI pair again before attaching the original rules-derived explanation. Each preview step describes actual development, captures, promotion, castling, checks or pressure changes, with a conditional principle. Expand position ideas on that exact preview board, then return to the unchanged lesson. Strongest found **at the budget** is not proven best; near-equal/bounded/mate comparison limits remain.

No source game, lesson move, policy, set revision, bookmark fingerprint or imported byte is rewritten. Position ideas are computed on demand on a cancellable worker, not in Compose rendering or the UI thread; only the expanded visible report is retained. Cursor/color changes cancel/discard old results; collapsing/leaving disposes work. Explicit loading/error states fail closed. This feature is available in Study, not automatically exposed in unaided Practice.

## Review and provenance

Prose is Ashva-original under Apache-2.0; no historical commentary or GM intention is copied/inferred. Attack/king-safety semantics were checked against [FIDE Laws, articles 3.1.3 and 3.9](https://handbook.fide.com/chapter/E012023) on 2026-10-02. Those laws establish move/attack rules, **not the strategic plans**. Family plans and trade-offs are original conditional teaching, not FIDE recommendations or independent expert review.

Semantic fixtures exercise French chain breaks, isolated/doubled/passed pawns, open files, both-color forks, pinned attacks versus illegal captures, blocked rays, terminal exclusions, cancellation and engine notation/history. Attacker queries are compared with the existing attack rule on every square/color across representative Ruy, London, Sicilian and pin positions; existing rules/perft tests also run. Native tests cover color/cursor changes, unchanged lessons, automatic practice hints and actual offline engine explanations. Actual gate outcomes belong in Relay.

This is local structured grounding, not expert certification, detailed endings for every opening or guaranteed wins. GM browsing/full-score teaching, recall scheduling and final board performance are separate milestones. No paid model or cloud service is configured.

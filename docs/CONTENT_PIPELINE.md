# Licensed content pipeline

Reviewed and measured on 2026-10-01. These are **source/data packs**, not finished teaching lessons. Android v0.3.0 includes both reviewed packs and can install them into Room KMP/SQLite through Offline library & sources. The teaching catalog still has seven authored openings/thirteen short lines; broader sourced search/teaching and GM screens remain separate tasks. See [offline storage](OFFLINE_STORAGE.md).

## Source and rights audit

| Collection | Evidence and decision | Boundary |
| --- | --- | --- |
| Opening-name taxonomy | [Lichess chess-openings copyright notice at the pinned revision](https://github.com/lichess-org/chess-openings/blob/c67912be581f0793dbaa776be5ccf111e01f88d9/README.md#copyright): facts are public domain; curation is CC0. Import all five TSVs and retain COPYING. | Names, ECO labels and legally replayable canonical routes. Not authored lessons, statistics or every theoretical reply. |
| Official monthly broadcast exports | [Broadcast collection notice](https://database.lichess.org/#broadcasts) explicitly specifies **CC BY-SA 4.0**, unlike the site's standard-game CC0 collection. Import one small archived month, April 2020, as a pipeline fixture. | Retain source/game URLs, contributors, license link and changes; derived game data stays CC BY-SA 4.0. No implied endorsement. No verified GM roster or complete careers. |
| Masters Explorer statistics / individual PGNs | [Masters endpoint specification](https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/tags/openingexplorer/masters.yaml) and [PGN specification](https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/tags/openingexplorer/masters-pgn-gameId.yaml) inspected. Collection redistribution permission was not established by this audit. **Not ingested.** | Defaults are 12 popular moves and 15 example games (15 maximum), not an exhaustive repertoire. The inspected spec advertises OAuth2 security; verify actual access/auth before integration, never assume anonymous permission. No tokens/account setup performed. |
| Per-round broadcast API | [Round PGN spec](https://raw.githubusercontent.com/lichess-org/api/master/doc/specs/tags/broadcasts/api-broadcast-round-broadcastRoundId-pgn.yaml) inspected; it advertises OAuth2 `study:read` security. **Not used.** | The acquired monthly database export is a different public source; do not generalize its access/license to every API or user study. |
| Other historical archives / user studies / publisher commentary | No additional archive or annotation rights were cleared. [Lichess terms](https://lichess.org/terms-of-service) do not make all submitted content public domain. **Not acquired or redistributed.** | Public availability, user PGN import permission, and an API server's code license do not establish publication rights for third-party annotations. Audit each source separately. |
| Evaluation datasets / engine binaries | No evaluation collection was acquired and no engine was bundled. | Dataset permissions and engine distribution obligations need separate audits. Source evaluation comments are not reproducible analysis or reviewed teaching. |

The broadcast pack and original raw broadcast export retain their collection's [CC BY-SA 4.0 license](https://creativecommons.org/licenses/by-sa/4.0/), attribution and modification notice. ShareAlike applies to adapted game data; do not erase provenance or add restrictions to that data. This is not a declaration that unrelated application code is CC BY-SA. Reassess the actual packaging/distribution obligations before release.

## Measured vertical slice

The target for this ingestion milestone is: attempt every nonempty row in all five pinned taxonomy files, attempt every lexically framed record in the chosen bounded archive, report a disposition for each, and legally replay every accepted full mainline. It is **not** a release claim of complete opening theory, curated teaching or famous-game coverage.

| Metric | Opening taxonomy | April 2020 broadcasts |
| --- | --- | --- |
| Input / accepted records | 3,815 / 3,815 | 79 / 79 |
| Exact duplicates / quarantined | 0 / 0 | 0 / 0 |
| Name-derived families / distinct names | 149 / 3,174 | Not applicable |
| Half-moves per route / game | 1–36 | 31–205 |
| Distinct normalized positions | 3,815 route endpoints | 6,843 across full mainlines |
| Total original game half-moves | Not applicable | 7,606 |
| Games matched to last known taxonomy position | Not applicable | 79 |
| Source-scoped unresolved player names | Not applicable | 15 |

Family is the source name before its first colon, not a reviewed ontology of every popular opening. Multiple named rows may share a name without sharing their final position. This snapshot has zero duplicate/transposed endpoints; synthetic tests verify multiple routes/labels at the same position and real-game recognition uses positions, not a required canonical move order. Half-move depth is not a number of complete moves.

The archived feed includes online aliases and source-reported ratings/titles. Do not guess that an alias is a specific GM, trust placeholder ratings, or present these records as a verified famous-game shelf. A supplied FIDE ID is labeled **reported by source**, not independently verified. Without it, identity is scoped to the source and normalized displayed name.

An accepted score has a known result, required event/player metadata and a legal mainline. That does not independently prove historical authenticity, source completeness, player identity or a guaranteed winning strategy. Original comments, NAGs, clocks, evaluations and RAVs remain in the raw file only; derived scores omit them. All provided RAV moves are validated before stripping, so invalid annotated variations quarantine their record too.

## Reproduce locally

Requirements: configured project JDK/Gradle, Node 22+, and an existing `zstd` executable on PATH. No database server, provider account or paid service is required. Do not silently install tools or create accounts.

```bash
# Verify cached raw inputs; fetch only missing locked files, serially.
node scripts/fetch-content.mjs

# Validate sources and publish immutable data packs; identical reruns are safe.
./gradlew :contentTools:run --args=import

# Rebuild records from raw sources and compare every pack byte without writing.
./gradlew :contentTools:run --args=verify

# Import, identity, legal replay, checksum/publication and download-policy tests.
node --test scripts/fetch-content.test.mjs
./gradlew :shared:jvmTest :contentTools:test
```

`--record-lock` is only for explicitly reviewed first acquisition when no lock exists. It acquires from the provider, not arbitrary cached input; it refuses to replace an existing lock. Do not delete the current lock to make a changed source pass. Introduce a separately reviewed snapshot/version for updates. The pinned taxonomy revision is `c67912be581f0793dbaa776be5ccf111e01f88d9`; broadcast bytes are pinned even though monthly files could change upstream. The broadcast compressed checksum was also compared with the provider's published [SHA-256 list](https://database.lichess.org/broadcast/sha256sums.txt): `8c39dfedff509c36a37a017d2be5dd066b1e1dbb9e62a6bf63b16c018d496725`.

Follow [provider rate-limit guidance](https://lichess.org/page/api-tips): one request at a time; a 429 stops the run and persists a full-minute minimum cooldown, respecting a longer numeric or HTTP-date Retry-After. There are no hidden retries or fixed assumed quotas. Only HTTPS on the two audited static-source hosts is allowed, redirects are rejected, each download is bounded to 2 MiB/30 seconds, and decoded PGN to 8 MiB/64 MiB zstd memory/30 seconds. Larger future sources need explicit chunking/limits, not silently raised bounds or whole-corpus downloads.

## Files, identities and integrity

- `content/sources.json`: manually reviewed source, collection license/evidence, revision, attribution, transformations and scope. Changes invalidate the lock's configuration checksum.
- `content/snapshots.lock.json`: acquisition time, URL, byte count and SHA-256 of each raw input and decoded archive. A checksum provides pinned integrity, not a digital signature or independent source authenticity.
- `content/raw/`: unmodified acquired bytes plus checksum-verified decompression; retained for audit and reproducibility.
- `content/packs/<source>-<revision-prefix>-import-v1/`: `manifest.json`, `ATTRIBUTION.txt`, `openings.jsonl`, `games.jsonl`, and `issues.jsonl`. Empty payloads are zero-byte files, not missing data. The two packs total roughly 2.8 MB plus manifests/notices; Android explicitly packages only those reviewed version directories through generated assets, not raw inputs or arbitrary new packs.
- `shared/.../content/`: Android-free serializable schema v1, sourced position matching, framing/validation and full-score-to-lesson-graph conversion. `contentTools` is the JVM CLI reusing the **same KMP legal rules**, not a second chess engine. Shared JVM target is tooling, not a replacement Android/iOS target.

Each manifest records processor/schema versions, source rights, input checksums, snapshot-lock checksum, coverage, payload hashes and limits. Game packs declare the exact taxonomy pack ID/hash used for naming; the mobile repository validates this retained dependency and recomputes each match before activation. A name match is not a teaching plan.

Opening identity hashes ECO, displayed name and legal UCI sequence; different move paths are preserved even when normalized positions coincide. Game identity hashes source-scoped/reported players, event/date/round/board/time, initial FEN, complete UCI sequence and result. Source GameURL is retained; where absent, a metadata-derived source key is used. Same score/identity merges occurrence references and external IDs. Conflicting versions of the same source key quarantine **all** versions. Metadata-derived key collisions intentionally fail closed and need review, not automatic overwrite or guessed reconciliation.

Every issue identifies source/file/row-or-frame ordinal and raw-record hash, with a bounded diagnostic and retained ID for duplicates. Invalid, incomplete, unsupported and conflicting records are distinct from exact duplicates. Each input row/frame satisfies `input = accepted + duplicates + quarantined`. Archive framing respects quotes/comments/RAV nesting. A corrupt unclosed comment/variation is one quarantined tail frame; the importer does not claim to recover individual games hidden inside it. No record silently disappears.

Publication writes a new pack to a private staging directory and exposes it with one atomic directory rename. Existing identical packs are accepted; different bytes, unexpected files and missing packs in verify mode fail. Processor/output changes require a new pack version; `import-v1` and `opening-lab-import/1` must advance together. Local publication is distinct from [mobile atomic installation/migration/rollback](OFFLINE_STORAGE.md); neither is a live remote download service.

## What comes next

Wire the installed sourced index/search and repertoire catalog into Compose, then connect original-game replay to the teaching player. Broader game sources, verified GM aliases, reviewed White/Black plans, deeper variations and bounded engine alternatives remain separate tasks. No server was provisioned, no account configured, no paid integration selected, and no promise of every possible line was made.

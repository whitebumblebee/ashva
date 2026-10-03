<!-- SPDX-License-Identifier: Apache-2.0 -->
# Players and original games

Android 0.13 added **Players & GM games** on Learn (Relay0028). Verified0.14 additionally teaches each original move and provides separate engine alternatives/preview/return (Relay0031). The library and the [game coach](HISTORICAL_GAME_COACH.md) remain distinct from verified complete careers or expert historical commentary.

## Content and identities

The shelf lazily indexes active installed broadcast packs, plus private user imports. Reviewed January/April2020 packs contain857/79 accepted scores respectively; installing both exposes936 source records. They are finite broadcast samples, not every GM match, a full career archive, representative popularity or proof of historical authenticity. Quarantined inputs stay excluded. Install packs in **Offline library & sources**; no automatic player API or live downloader is called.

Names, titles and FIDE IDs are source-reported, not independently authenticated. Aliases group only by exact reported identity; similar names and private identities remain separate. The reported-GM filter does not guess missing titles. Exact duplicate score IDs merge origins only when metadata/moves agree; conflicts fail closed. Different IDs are not guessed to be the same real-world game.

Following starts empty. Search installed players and choose **Follow**. **Show games** selects that exact identity, and filters combine query, event, year, opening/ECO, selected-player color, result, followed players and reported GM title. Unfollowing keeps every game. A retained identity without installed scores shows unavailable sample history, not an empty career. Results and player pagination publish their counts; no moves or scores are silently dropped.

## Original replay and persistence

Open any score as White or Black. First/previous/next/last, full move-list jumps, autoplay/pause, speed and flip operate on the original mainline. The result never changes; resignation/agreement can precede a board-terminal position. Navigating away/backgrounding pauses playback. Legal preparation/filtering is cancellable and away from Main; a failed new load cannot display the previous game as the requested one.

Each public bookmark pins record ID, exact retained pack ID and manifest hash; private bookmarks pin the canonical private record. Content fingerprints and replay paths are checked on cold restore. A newer active pack is not silently substituted. Missing/corrupt exact content leaves the old bookmark retained with an explicit unavailable message. Original-game study does not write an opening selection, recall attempt or mastery score.

The on-demand position report and per-move coach contain Ashva board facts and conditional plans, not historical intention. Original user comments are visibly unverified. Bounded engine comparisons/continuations have explicit hypothetical labels and return to the unchanged original; see [the coaching contract](HISTORICAL_GAME_COACH.md).

## Private PGNs

**Import private PGN** accepts one legal standard game, up to256KiB UTF-8/4096 mainline half-moves. Archives, unsupported variants, illegal moves and oversized input are explicitly rejected. Original mainline/result/comments/recursive variations are retained; this player's initial UI replays the mainline. Supplied annotations/identities remain user-supplied and unverified. Imports are local learner data, not redistributed, licensed as public content, or included in public observed counts. The user must have permission for supplied commentary.

Canonical duplicate imports are idempotent, preserving the first retained metadata record. Storage is bounded at1000 private records/32MiB payload, with a1MiB per-record JSON bound; reaching a bound rejects a new import without pruning. The dialog does not place a large PGN draft in an Android saved-state Bundle. Successful saved-game references are small.

Schema5 adds `followed_players` and `private_games` through explicit4→5 migration; Android/JVM/iOS builders register1→2→3→4→5. Old learner/content/policy/set rows and versions remain intact. No destructive migration, owner database clearing, network account or cross-device sync is introduced. Uninstalling/clearing data still loses local learner imports/follows/progress; no export/recovery UI is available yet.

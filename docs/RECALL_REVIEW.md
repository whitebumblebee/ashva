# Local recall and chosen-scope progress

Android0.15 replaces sample Home/Review/Profile counts with local SQLite schedules and actual events. Verification status is recorded in Relay; this is not an externally validated measure of chess strength or a guarantee of remembering an opening.

## Scope and identity

Starting Practice enrolls the chosen single route and color. Practicing a saved family enrolls all original routes admitted by that exact policy revision; a checked named set enrolls every pinned member. Review lists the last practiced revision of each scope, with its revision in the title. Editing a family does not silently adopt it into a set or existing review scope: practice the new revision deliberately. Missing/incompatible content is reported on opening a card, not substituted or removed.

Cards bind the immutable course/lesson move snapshot, initial FEN, complete ordered prefix, expected UCI move and learner color. Identical prefixes share one decision within a course. Transpositions/repetitions with different histories stay separate, even if the board looks identical; this avoids losing draw/path context. Different courses are not assumed equivalent. A changed move snapshot creates different cards; original history remains retained. A prose-only edit does not reset the moves-only identity. Policy revisions with unchanged decisions can reuse the existing exact cards. Membership retains exact policy references and a representative admitted path, independently of the first stored card's display metadata.

Completion denominator is the chosen finite decision set, not all taxonomy routes or mathematical chess continuations. A scope may be a route, family or named set; overlapping scope counts are not added into a fabricated global percentage. An exploratory transposition branch whose actual prefix is outside the recorded scope is saved as explicitly ungraded history.

## What counts

- Unaided: the expected move was played without exposed help. This is recall of the chosen move, not proof that other legal moves are weak.
- Assisted correct: the expected move follows an explicit hint, automatic hint, successful exposed engine output, confirmed branch adoption, or Study exposure within ten minutes. Assistance at a cursor survives cold bookmarks. Failed/stopped/late engine requests do not mark the new position assisted. Engine previews and original-game replay do not create recall grades.
- Not recalled: the attempted move differs from the expected move. A legal off-line move or another valid opening variation is not called a chess blunder. The existing automatic hint/unchanged-board behavior remains.
- Study views: entering Full idea/replay is counted separately; jumping through a line does not manufacture successful answers.
- Legacy/ungraded: prior attempts lack trustworthy version/engine-exposure context, so they remain visible but never seed mastery or backfilled schedules.

The Profile counts actual stored attempts/grades and separate Study views. Its unaided-answer total includes early drills; it is not the number of spaced successes. Counts include both colors and retained history, with no invented streak, rating or probability.

## Deterministic scheduling

Ashva schedule version1 is an original, transparent conservative schedule: due unaided answers progress through intervals of1,3,7,14,30,60,120 and240 days. Assisted or unsuccessful answers reset spacing and become due after ten minutes. Repeated early answers cannot extend the due time or inflate spaced progress. Late/backwards-clock events remain in history but cannot roll scheduling state back. Due checks use UTC epoch milliseconds; dates are displayed in local time and refresh at least once per minute.

“Established” means at least three due unaided successes at separated intervals and not currently overdue. An overdue card is not currently established. This operational label is not permanent mastery, an empirically calibrated retention percentage or a chess rating. No FSRS/SM-2 implementation, fitted parameters, optimization, short-term learning research validation or notification service is claimed. The [Anki manual](https://docs.ankiweb.net/deck-options.html) informs the distinction between unsuccessful recall and successful-but-difficult answers; source/library version and licensing must be verified separately before adopting a model. No external scheduler code or dataset is copied here.

## Persistence and limits

Additive Room schema6 registers5→6 alongside1→2→3→4→5 on Android/JVM/shared iOS builders. It adds cards, immutable scopes/membership, active scope pointers, graded events and Study views without rewriting old rows. No foreign keys to replaceable content, pruning, destructive fallback or automatic revision rebinding. Attempts/events/card state commit atomically; exact attempt retries are idempotent, conflicting IDs fail without partial progress. Out-of-order grades do not overwrite a newer state.

Limits:10,000 cards/200,000 prefix half-moves per enrollment;512 half-moves per card;1,024 retained scopes and100,000 retained cards. Exceeding limits fails visibly without dropping decisions or deleting history. Review fetches one due card at a time (repository paging ceiling50). SQL aggregate flows avoid loading all historical attempt JSON. Graph preparation/legal enrollment validation run on cancellable workers; a64-entry serial writer preserves accepted event order. A failed write is retained in that bounded session queue: **Retry pending saves** in the trainer or Review retries its original event ID/time, not a new answer. A capacity-refused action is explicitly unsaved and must be attempted again after pending saves finish. Wait for Saved for offline resume before closing; failed in-memory writes cannot survive process death. Data still has no export/account-recovery UI and uninstall/clear removes it.

Review asks one exact chosen position, disables side/branch substitution, retains hints/review context across relaunch, and only enables Next after the answer's actual ledger write. Next waits behind pending grades, preventing stale due-card reselection. An interrupted answer is checked by its exact event ID, not guessed from an old card timestamp; retry from Review or restart. Original-game teaching and hypothetical exploration remain separate.

No network upload, account, paid provider, Git/publication or iOS client is added. Shared-iOS compilation checks the schema boundary only; the owner has deferred that application task.

## User check

Automated native regressions use stable Android Test Orchestrator1.6.1, one instrumentation process per case, with clearPackageData=false. Connected tests still belong only on an isolated test device: Gradle can uninstall target packages after the suite. Orchestration is a test-harness change, not an app memory/performance certification. See [official isolation guidance](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner) and [stable release notes](https://developer.android.com/jetpack/androidx/releases/test#orchestrator-1.6.1).

Cold/reopened synthetic database fixtures clear their ViewModel stores on Main, then join the cancelled scope jobs off Main before closing SQLite or starting a fresh model. Cancellation alone is not completion: see [ViewModel scope lifecycle](https://developer.android.com/reference/androidx/lifecycle/ViewModel) and [coroutine Job completion](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-job/). Production keeps its application-scoped database open; this test cleanup never closes the owner's learner database.

1. Practice a route as White, then a route as Black; return to Review and select the corresponding scope.
2. Read actual due/new, attempted and established counts. Start Review, play the shown position's expected move, wait for its save and use Next due position.
3. Use Hint or make a legal off-line move, then play the expected move. Profile must record assisted/not-recalled separately; established must not increase.
4. Analyze a Practice position successfully, close/reopen without clearing, then answer it: engine help remains assisted. A failed/stopped analysis must not add help.
5. Full idea and replay add Study views, not recall grades. Immediate practice after exposure counts as assisted; early drills cannot advance spacing.
6. Practice a named set to enroll all its pinned members; editing a family leaves the set scope/revision unchanged until deliberate membership/practice changes.
7. Wait for Saved for offline resume, force-stop/reopen, then Continue or Review. Exact scopes, hints, counts and scheduled dates survive. Old attempts stay ungraded; no demonstration equals full-repertoire mastery.

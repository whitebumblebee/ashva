package com.openinglab.shared.storage

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.chess.san
import com.openinglab.shared.content.*
import com.openinglab.shared.games.*
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.repertoire.RepertoirePolicy
import com.openinglab.shared.repertoire.RepertoireSet
import com.openinglab.shared.repertoire.RepertoirePolicyRef
import com.openinglab.shared.repertoire.RepertoireSetPlanner
import com.openinglab.shared.review.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import com.openinglab.shared.practice.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock

/** One instance per database. Validation precedes a single atomic content/active-version commit. */
class RoomLearningStore(
    private val database: LearningDatabase,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val installCheckpoint: suspend (String) -> Unit = {},
) : LearningStore {
    override val tactics = RoomTacticsStore(database)
    private val dao = database.learningDao()
    override fun learnerActivity(at: Long) = combine(dao.activityTimes(), dao.openingActivity(), dao.dueCount(at), dao.studyRoutes()) { times, sessions, due, views ->
        val events = sessions.map { StudyActivity(it.id, it.lessonId, it.pathId, it.kind, it.recordedAt) }
        // A route view identifies one line. Policy/set views do not identify the line actually viewed.
        val routeViews = views.mapNotNull { view ->
            val scope = json.decodeFromString<RecallScope>(view.scopePayload)
            if (!scope.groupId.startsWith("route:")) null else {
                val target = json.decodeFromString<RecallTarget>(view.target)
                val context = json.decodeFromString<RecallContext>(view.context)
                context.pathId?.let { path -> StudyActivity("view:${view.id}", target.lessonId, path, StudyActivity.STUDY, view.recordedAt) }
            }
        }
        LearnerActivity(times, events.filter { it.kind != StudyActivity.PUZZLE } + routeViews, due, events.filter { it.kind == StudyActivity.PUZZLE })
    }
    override suspend fun recordStudyActivity(activity: StudyActivity) {
        require(activity.id.isNotBlank() && activity.lessonId.isNotBlank() && activity.pathId.isNotBlank())
        require(activity.kind in listOf(StudyActivity.STUDY, StudyActivity.PRACTICE, StudyActivity.PUZZLE) && activity.recordedAt >= 0)
        val row = StudyActivityEntity(activity.id, activity.lessonId, activity.pathId, activity.kind, activity.recordedAt)
        database.useWriterConnection { it.immediateTransaction {
            val old = dao.openingActivityById(row.id)
            // A puzzle may be resumed on the same local day: retain its first timestamp.
            if (row.kind == StudyActivity.PUZZLE && old != null) {
                require(old.lessonId == row.lessonId && old.pathId == row.pathId && old.kind == row.kind)
                return@immediateTransaction
            }
            require(old == null || old == row) { "An opening session event is immutable" }
            if (old == null) dao.openingActivity(row)
        } }
    }
    private val installs = Mutex()
    override val availability = dao.availability().map { rows -> rows.map {
        PackAvailability(it.sourceId, it.requestedPackId, it.state, it.error, it.activePackId)
    } }

    override suspend fun recoverInterruptedInstalls() = installs.withLock { dao.recoverInterrupted() }

    override suspend fun install(bundle: PackBundle): Unit = installs.withLock {
        // Never take source IDs or status from untrusted/unbounded manifest bytes.
        val manifest = withContext(Dispatchers.Default) { checkedManifest(bundle) }
        dao.job(InstallJobEntity(manifest.source.id, manifest.packId, "LOADING", null))
        try {
            // Packs are immutable/retained. Do expensive naming checks before holding the writer;
            // the commit below rechecks dependency hashes atomically.
            val taxonomy = if (manifest.source.kind == SourceKind.BROADCAST_GAMES) {
                require(manifest.dependencies.size == 1) { "Game pack must declare its taxonomy dependency." }
                val dependency = manifest.dependencies.single()
                require(dao.packById(dependency.packId)?.manifestHash == dependency.manifestSha256) {
                    "Install the exact required opening pack first."
                }
                val taxonomyPayloads = dao.recordsInPack(dependency.packId, "OPENING")
                withContext(Dispatchers.Default) {
                    SourcedOpeningIndex(taxonomyPayloads.map { json.decodeFromString<OpeningRecord>(it) })
                }
            } else null
            val validated = withContext(Dispatchers.Default) { validatePayloads(bundle, manifest, taxonomy) }
            installCheckpoint("validated")
            database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
                for (dependency in manifest.dependencies) {
                    require(dao.packById(dependency.packId)?.manifestHash == dependency.manifestSha256) {
                        "Install the exact required opening pack first."
                    }
                }
                val existing = dao.packById(manifest.packId)
                require(existing == null || existing.manifestHash == bundle.expectedManifestSha256) { "Immutable pack ID already has different bytes." }
                if (existing == null) {
                    dao.pack(PackEntity(manifest.packId, manifest.source.id, json.encodeToString(manifest),
                        bundle.expectedManifestSha256, validated.notices))
                    // Batches bound SQL work without sacrificing transaction atomicity.
                    validated.records.chunked(200).forEach { dao.records(it) }
                    validated.positions.chunked(500).forEach { dao.positions(it) }
                }
                installCheckpoint("before-activation")
                dao.activate(ActivePackEntity(manifest.source.id, manifest.packId))
                dao.job(InstallJobEntity(manifest.source.id, manifest.packId, "DOWNLOADED", null))
            } }
        } catch (error: Exception) {
            withContext(NonCancellable) {
                // No raw PGN, user data or unbounded exception text in persisted diagnostics.
                val detail = if (error is CancellationException) "Installation interrupted; retry when ready."
                    else (error.message ?: "Installation failed.").filterNot { it.isISOControl() }.take(240)
                dao.job(InstallJobEntity(manifest.source.id, manifest.packId, "ERROR", detail))
            }
            throw error
        }
    }

    override suspend fun activatePreviousVersion(sourceId: String, packId: String): Unit = installs.withLock {
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            val pack = requireNotNull(dao.packById(packId)) { "Requested version is not installed." }
            require(pack.sourceId == sourceId)
            val manifest = json.decodeFromString<ContentManifest>(pack.manifest)
            manifest.dependencies.forEach { require(dao.packById(it.packId)?.manifestHash == it.manifestSha256) }
            dao.activate(ActivePackEntity(sourceId, packId))
            dao.job(InstallJobEntity(sourceId, packId, "DOWNLOADED", null))
        } }
    }
    override suspend fun activePacks() = dao.activePacks().map {
        InstalledPack(json.decodeFromString<ContentManifest>(it.manifest), it.manifestHash, it.notices)
    }
    override suspend fun retainedPack(packId: String) = dao.packById(packId)?.let {
        InstalledPack(json.decodeFromString<ContentManifest>(it.manifest), it.manifestHash, it.notices)
    }
    override suspend fun openings(packId: String?) = (if (packId == null) dao.records("OPENING") else dao.recordsInPack(packId, "OPENING"))
        .map { json.decodeFromString<OpeningRecord>(it) }
    override suspend fun openingIdsAt(positionKey: String) = dao.openingIdsAt(positionKey)
    override suspend fun game(id: String) = dao.game(id)?.let { json.decodeFromString<GameRecord>(it) }
    override suspend fun games(packId: String?) = withContext(Dispatchers.Default) {
        (if (packId == null) dao.records("GAME") else dao.recordsInPack(packId, "GAME"))
            .map { ensureActive(); json.decodeFromString<GameRecord>(it) }
    }
    override suspend fun gameInPack(packId: String, id: String) = dao.gameInPack(packId, id)?.let { json.decodeFromString<GameRecord>(it) }
    override val followedPlayers = dao.followedPlayers().map { values ->
        withContext(Dispatchers.Default) { values.map { json.decodeFromString<FollowedPlayer>(it).also { player -> player.validate() } } }
    }
    override suspend fun followPlayer(player: FollowedPlayer) {
        player.validate()
        dao.follow(FollowedPlayerEntity(player.id, json.encodeToString(player)))
    }
    override suspend fun unfollowPlayer(id: String) { require(id.length in 1..160); dao.unfollow(id) }
    override val privateGames = dao.privateGames().map { values ->
        withContext(Dispatchers.Default) { values.map { ensureActive(); json.decodeFromString<PrivateGameRecord>(it) } }
    }
    override suspend fun privateGame(id: String) = dao.privateGame(id)?.let { json.decodeFromString<PrivateGameRecord>(it) }
    override suspend fun importPrivateGame(text: String): PrivateGameRecord {
        val record = withContext(Dispatchers.Default) { PrivateGameRecord.import(text) }
        val payload = withContext(Dispatchers.Default) { json.encodeToString(record).also { require(it.encodeToByteArray().size <= 1024 * 1024) } }
        return database.useWriterConnection { connection -> connection.immediateTransaction {
            val existing = dao.privateGame(record.id)
            if (existing != null) {
                val retained = json.decodeFromString<PrivateGameRecord>(existing)
                require(retained.canonicalPgn == record.canonicalPgn) { "Private game identity conflict" }
                retained // Idempotent: preserve first reported metadata, never overwrite or publicize it.
            } else {
                require(dao.privateGameCount() < 1000 && dao.privateGameBytes() + payload.encodeToByteArray().size <= 32L * 1024 * 1024) {
                    "Private library storage limit reached; existing imports remain retained"
                }
                dao.privateGame(PrivateGameEntity(record.id, payload, now()))
                record
            }
        } }
    }
    override suspend fun latestBookmark() = dao.latestBookmark()?.let { json.decodeFromString<LessonBookmark>(it.payload) }
    override suspend fun saveBookmark(bookmark: LessonBookmark) {
        require(bookmark.replay.branches.size <= com.openinglab.shared.lesson.LessonReplay.MAX_BRANCH_DEPTH)
        require(bookmark.mistakes >= 0 && bookmark.assisted >= 0 && bookmark.contentVersion.matches(hashPattern))
        require((bookmark.repertoireId == null) == (bookmark.repertoireRevision == null))
        require(listOf(bookmark.repertoireSetId, bookmark.repertoireSetRevision, bookmark.repertoireSetIndex).count { it != null } in listOf(0, 3))
        require((bookmark.reviewScopeId == null) == (bookmark.reviewTargetId == null))
        require(bookmark.studyExposedAt == null || bookmark.studyExposedAt in 0..RecallScheduler.MAX_TIME)
        bookmark.reviewTargetId?.let { id ->
            val card = requireNotNull(recallCard(requireNotNull(bookmark.reviewScopeId), id))
            require(bookmark.gameReference == null && card.target.lessonId == bookmark.lessonId && card.target.side == bookmark.replay.playerSide)
            require(!bookmark.reviewAnswered || bookmark.reviewAttemptId?.length?.let { it in 1..160 } == true)
        }
        bookmark.gameReference?.let { ref ->
            ref.validate()
            require(bookmark.lessonId == ref.lessonId && bookmark.repertoireId == null && bookmark.repertoireSetId == null)
            require(bookmark.mode == "STUDY" && bookmark.replay.branches.isEmpty()) { "An original-game bookmark cannot replace its mainline" }
            if (ref.privateImport) require(dao.privateGame(ref.recordId) != null) { "Private original game is unavailable" }
            else require(dao.packById(requireNotNull(ref.packId))?.manifestHash == ref.manifestSha256 &&
                dao.gameInPack(ref.packId, ref.recordId) != null) { "Exact original game source is unavailable" }
        }
        bookmark.repertoireId?.let { id ->
            val policy = requireNotNull(repertoirePolicy(id, bookmark.repertoireRevision)) { "Saved repertoire revision is unavailable" }
            require(policy.lessonId == bookmark.lessonId && policy.side == bookmark.replay.playerSide)
        }
        bookmark.repertoireSetId?.let { id ->
            val set = requireNotNull(repertoireSet(id, bookmark.repertoireSetRevision))
            require(set.side == bookmark.replay.playerSide && bookmark.repertoireSetIndex!! in 0 until RepertoireSetPlanner.MAX_ITEMS)
            require(RepertoirePolicyRef(requireNotNull(bookmark.repertoireId), requireNotNull(bookmark.repertoireRevision)) in set.members)
        }
        dao.bookmark(BookmarkEntity(bookmark.lessonId, json.encodeToString(bookmark), now()))
    }
    override suspend fun recordAttempt(attempt: LearningAttempt) = dao.attempt(
        AttemptEntity(attempt.id, attempt.lessonId, json.encodeToString(attempt), attempt.recordedAt))
    override fun recallScopes(at: Long) = dao.recallScopes(at).map { rows -> withContext(Dispatchers.Default) {
        rows.map { RecallScopeSummary(json.decodeFromString(it.payload), it.total, it.introduced, it.established, it.due, it.nextDueAt) }
    } }
    override val supportsRecall = true
    override val learningTotals = dao.learningTotals().map { row ->
        LearningTotals(row.attempts, row.unaided, row.assisted, row.notRecalled, row.legacyUngraded, row.studyViews)
    }
    override suspend fun enrollRecall(enrollment: RecallEnrollment) = withContext(Dispatchers.Default) {
        val context = currentCoroutineContext()
        enrollment.validate { context.ensureActive() }
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            val scope = enrollment.scope
            val payload = json.encodeToString(scope)
            val existingScope = dao.recallScope(scope.id)
            require(existingScope == null || json.decodeFromString<RecallScope>(existingScope.payload).let {
                it.id == scope.id && it.groupId == scope.groupId && it.setId == scope.setId && it.setRevision == scope.setRevision
            }) { "Immutable review scope conflict; existing progress retained" }
            // Validate exact stored policy/set references before creating any rows.
            val policies = mutableMapOf<RepertoirePolicyRef, RepertoirePolicy>()
            for (entry in enrollment.entries) entry.context.policy?.let { ref ->
                val policy = policies.getOrPut(ref) {
                    json.decodeFromString(requireNotNull(dao.policyVersion(ref.id, ref.revision)).payload)
                }
                require(policy.lessonId == entry.target.lessonId && policy.contentVersion == entry.target.contentVersion && policy.side == entry.target.side)
                var board = BoardPosition.fromFen(entry.target.initialFen)
                for (uci in entry.target.prefix + entry.target.expectedUci) {
                    require(if (board.sideToMove == policy.side) policy.preferredMoves[board.positionKey] == uci
                        else uci in policy.opponentReplies[board.positionKey].orEmpty())
                    board = board.apply(com.openinglab.shared.model.ChessMove.fromUci(uci))
                }
            }
            if (scope.setId != null) {
                val set = json.decodeFromString<RepertoireSet>(requireNotNull(dao.setVersion(scope.setId, requireNotNull(scope.setRevision))).payload)
                require(policies.keys == set.members.toSet()) { "No set members may be omitted" }
            }
            if (existingScope == null) {
                require(dao.recallScopeCount() < 1024) { "Review scope limit reached; previous revisions retained" }
                var cards = dao.recallCardCount()
                for (entry in enrollment.entries) {
                    val old = dao.recallCard(entry.target.id)
                    if (old == null) {
                        require(cards++ < 100_000) { "Review card limit reached; no progress deleted" }
                        dao.recallCard(RecallCardEntity(entry.target.id, json.encodeToString(entry.target), json.encodeToString(RecallState()), 0, null, 0))
                    } else require(json.decodeFromString<RecallTarget>(old.target).identity() == entry.target.identity())
                }
                dao.recallScope(RecallScopeEntity(scope.id, payload))
                enrollment.entries.chunked(200).forEach { entries -> dao.recallMemberships(entries.map {
                    RecallScopeCardEntity(scope.id, it.target.id, json.encodeToString(it.context))
                }) }
            }
            dao.activateRecallScope(ActiveRecallScopeEntity(scope.groupId, scope.id))
        } }
    }
    override suspend fun recallCards(scopeId: String, at: Long, limit: Int): List<RecallCard> {
        require(scopeId.matches(hashPattern) && at in 0..RecallScheduler.MAX_TIME && limit in 1..50)
        return withContext(Dispatchers.Default) { dao.recallCards(scopeId, at, limit).map {
            RecallCard(json.decodeFromString(it.target), json.decodeFromString(it.state), json.decodeFromString(it.context))
        } }
    }
    override suspend fun recallCard(scopeId: String, cardId: String): RecallCard? {
        require(scopeId.matches(hashPattern) && cardId.matches(hashPattern))
        return withContext(Dispatchers.Default) { dao.scopedRecallCard(scopeId, cardId)?.let {
            RecallCard(json.decodeFromString(it.target), json.decodeFromString(it.state), json.decodeFromString(it.context))
        } }
    }
    override suspend fun hasRecallEvent(attemptId: String, cardId: String) = dao.recallEvent(attemptId)?.cardId == cardId
    override suspend fun recordRecall(event: RecallEvent) {
        withContext(Dispatchers.Default) { event.validate() }
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            requireNotNull(dao.recallMembership(event.scopeId, event.target.id)) { "Review decision is outside the chosen scope" }
            val payload = json.encodeToString(event)
            val existing = dao.recallEvent(event.attempt.id)
            if (existing != null) { require(existing.payload == payload) { "Immutable attempt conflict" }; return@immediateTransaction }
            val card = requireNotNull(dao.recallCard(event.target.id))
            require(json.decodeFromString<RecallTarget>(card.target).identity() == event.target.identity())
            val old = json.decodeFromString<RecallState>(card.state)
            val next = RecallScheduler.grade(old, event.grade, event.attempt.recordedAt)
            dao.attempt(AttemptEntity(event.attempt.id, event.attempt.lessonId, json.encodeToString(event.attempt), event.attempt.recordedAt))
            dao.recallEvent(RecallEventEntity(event.attempt.id, card.id, event.grade.name, event.attempt.recordedAt, payload))
            dao.recallCard(card.copy(state = json.encodeToString(next), dueAt = next.dueAt, lastAt = next.lastAt, spacedSuccesses = next.spacedSuccesses))
        } }
    }
    override suspend fun recordStudyView(id: String, scopeId: String, at: Long) {
        require(id.length in 1..160 && scopeId.matches(hashPattern) && at in 0..RecallScheduler.MAX_TIME)
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            requireNotNull(dao.recallScope(scopeId))
            val value = StudyViewEntity(id, scopeId, at)
            val old = dao.studyView(id)
            require(old == null || old == value)
            if (old == null) dao.studyView(value)
        } }
    }
    override suspend fun attempts(lessonId: String) = dao.attempts(lessonId).map { json.decodeFromString<LearningAttempt>(it) }
    override suspend fun selectRepertoire(selection: RepertoireSelection) = dao.repertoire(
        RepertoireEntity(selection.lessonId, json.encodeToString(selection)))
    override suspend fun repertoires() = dao.repertoires().map { json.decodeFromString<RepertoireSelection>(it) }

    override val repertoirePolicies = dao.repertoirePolicies().map { rows ->
        withContext(Dispatchers.Default) { rows.map { json.decodeFromString<RepertoirePolicy>(it.payload).also { policy -> policy.validate() } } }
    }
    override suspend fun repertoirePolicy(id: String, revision: Int?): RepertoirePolicy? =
        (if (revision == null) dao.currentPolicy(id) else dao.policyVersion(id, revision))?.let {
            json.decodeFromString<RepertoirePolicy>(it.payload).also { policy -> policy.validate() }
        }

    override suspend fun saveRepertoirePolicy(policy: RepertoirePolicy) {
        val payload = withContext(Dispatchers.Default) {
            policy.validate()
            json.encodeToString(policy).also { require(it.encodeToByteArray().size <= 2 * 1024 * 1024) { "Repertoire exceeds storage limit" } }
        }
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            val current = dao.currentPolicy(policy.id)
            val existing = dao.policyVersion(policy.id, policy.revision)
            if (existing != null) {
                require(json.decodeFromString<RepertoirePolicy>(existing.payload) == policy) { "A saved repertoire revision cannot be overwritten" }
                return@immediateTransaction // Idempotent retry must never roll the active pointer backward.
            }
            require(policy.revision == (current?.revision?.plus(1) ?: 0)) { "Repertoire changed elsewhere; reopen the editor before saving." }
            current?.let {
                val previous = json.decodeFromString<RepertoirePolicy>(it.payload)
                require(previous.lessonId == policy.lessonId && previous.contentVersion == policy.contentVersion && previous.side == policy.side)
            }
            dao.policy(RepertoirePolicyEntity(policy.id, policy.revision, policy.lessonId, payload, now()))
            dao.activatePolicy(ActiveRepertoirePolicyEntity(policy.id, policy.revision))
        } }
    }

    override val repertoireSets = dao.repertoireSets().map { rows ->
        withContext(Dispatchers.Default) { rows.map { json.decodeFromString<RepertoireSet>(it.payload).also { set -> set.validate() } } }
    }
    override suspend fun repertoireSet(id: String, revision: Int?): RepertoireSet? =
        (if (revision == null) dao.currentSet(id) else dao.setVersion(id, revision))?.let {
            json.decodeFromString<RepertoireSet>(it.payload).also { set -> set.validate() }
        }

    override suspend fun saveRepertoireSet(set: RepertoireSet) {
        val payload = withContext(Dispatchers.Default) {
            set.validate()
            json.encodeToString(set).also { require(it.encodeToByteArray().size <= 128 * 1024) }
        }
        database.useWriterConnection { connection -> connection.immediateTransaction<Unit> {
            val current = dao.currentSet(set.id)
            val existing = dao.setVersion(set.id, set.revision)
            if (existing != null) {
                require(json.decodeFromString<RepertoireSet>(existing.payload) == set) { "Saved set revisions cannot be overwritten" }
                return@immediateTransaction
            }
            require(set.revision == (current?.revision?.plus(1) ?: 0)) { "Set changed elsewhere; reopen before saving" }
            current?.let { require(json.decodeFromString<RepertoireSet>(it.payload).side == set.side) { "White and Black sets stay separate" } }
            for (ref in set.members) {
                val policy = requireNotNull(dao.policyVersion(ref.id, ref.revision)) { "A selected family revision is unavailable" }
                require(json.decodeFromString<RepertoirePolicy>(policy.payload).side == set.side)
            }
            dao.repertoireSet(RepertoireSetEntity(set.id, set.revision, payload, now()))
            dao.activateSet(ActiveRepertoireSetEntity(set.id, set.revision))
        } }
    }

    companion object {
        private val json = Json { encodeDefaults = true }
        private val hashPattern = Regex("[a-f0-9]{64}")
        private val payloadNames = setOf("openings.jsonl", "games.jsonl", "issues.jsonl", "ATTRIBUTION.txt")

        private fun checkedManifest(bundle: PackBundle): ContentManifest {
            require(bundle.manifest.size in 1..65_536) { "Manifest exceeds limits." }
            require(bundle.expectedManifestSha256.matches(hashPattern) && contentSha256(bundle.manifest) == bundle.expectedManifestSha256) {
                "Manifest checksum does not match the trusted catalog."
            }
            val m = json.decodeFromString<ContentManifest>(bundle.manifest.decodeToString(throwOnInvalidSequence = true))
            require(m.schemaVersion == 1 && m.processorVersion == "opening-lab-import/1") { "Unsupported content schema/processor." }
            require(m.packId.matches(Regex("[a-z0-9-]{1,120}")) && m.source.id.matches(Regex("[a-z0-9-]{1,100}")))
            require(m.source.redistributionApproved && m.source.attribution.isNotBlank() && m.source.modifications.isNotBlank())
            require(m.source.license == if (m.source.kind == SourceKind.OPENING_TAXONOMY) "CC0-1.0" else "CC-BY-SA-4.0")
            require(m.snapshotLockSha256.matches(hashPattern) && m.dependencies.all { it.manifestSha256.matches(hashPattern) })
            require(m.files.size == payloadNames.size && m.files.map { it.name }.toSet() == payloadNames && bundle.files.keys == payloadNames) {
                "Pack files are missing, duplicated or unexpected."
            }
            require(m.files.all { it.bytes in 0..8_388_608 && it.sha256.matches(hashPattern) } && m.files.sumOf { it.bytes } <= 12_582_912)
            return m
        }

        private data class Validated(val records: List<ContentRecordEntity>, val positions: List<PositionEntity>,
            val games: List<GameRecord>, val notices: String)

        private suspend fun validatePayloads(bundle: PackBundle, m: ContentManifest, taxonomy: SourcedOpeningIndex?): Validated {
            m.files.forEach {
                val bytes = bundle.files.getValue(it.name)
                require(bytes.size.toLong() == it.bytes && contentSha256(bytes) == it.sha256) { "Payload checksum/size mismatch: ${it.name}" }
            }
            fun text(name: String) = bundle.files.getValue(name).decodeToString(throwOnInvalidSequence = true)
            fun lines(name: String, limit: Int): List<String> {
                val rows = text(name).lineSequence().filter { it.isNotEmpty() }.toList()
                require(rows.size <= limit && rows.all { it.length <= 524_288 }) { "Record count/size exceeds limits." }
                return rows
            }
            val openings = lines("openings.jsonl", 50_000).map { json.decodeFromString<OpeningRecord>(it) }
            val games = lines("games.jsonl", 10_000).map { json.decodeFromString<GameRecord>(it) }
            val issues = lines("issues.jsonl", 50_000).map { json.decodeFromString<ImportIssue>(it) }
            require(if (m.source.kind == SourceKind.OPENING_TAXONOMY) games.isEmpty() && m.dependencies.isEmpty() else openings.isEmpty())
            val records = mutableListOf<ContentRecordEntity>()
            val positions = mutableListOf<PositionEntity>()
            fun locations(values: List<SourceLocation>) {
                require(values.isNotEmpty() && values.all { it.sourceId == m.source.id && it.ordinal > 0 && it.rawSha256.matches(hashPattern) })
            }
            for (opening in openings) {
                currentCoroutineContext().ensureActive()
                require(opening.uci.size in 1..128 && opening.uci.size == opening.san.size)
                val parsed = SourceValidation.opening(opening.eco, opening.name, opening.san.joinToString(" "))
                require(parsed.line.plies.map { it.move.uci } == opening.uci && parsed.line.plies.map { it.san } == opening.san)
                val path = parsed.positions()
                require(path.last().toFen() == opening.finalFen && path.last().positionKey == opening.positionKey)
                require(opening.family == opening.name.substringBefore(':').trim())
                require(opening.id == "opening-" + contentSha256("${opening.eco}\n${opening.name}\n${opening.uci.joinToString(" ")}".encodeToByteArray()))
                locations(opening.occurrences)
                records += ContentRecordEntity(m.packId, opening.id, "OPENING", opening.name, opening.positionKey, json.encodeToString(opening))
                positions += path.mapIndexed { ply, p -> PositionEntity(m.packId, opening.id, ply, p.positionKey) }
            }
            for (game in games) {
                currentCoroutineContext().ensureActive()
                val parsed = SourceValidation.game(game.canonicalPgn)
                val path = parsed.positions()
                // Export supplies unknown roster fields which may not exist in the original feed.
                // Compare through that exact normalization, not by dropping source metadata checks.
                require(game.tags["Result"] == null || game.tags["Result"] == game.result) { "Source result metadata disagrees" }
                require(game.tags["SetUp"] == null || game.tags["SetUp"] in setOf("0", "1")) { "Invalid source SetUp metadata" }
                require((game.tags["SetUp"] == "1") == (game.tags["FEN"] != null)) { "Source FEN/SetUp metadata disagrees" }
                val sourceInitial = game.tags["FEN"]?.let { BoardPosition.fromFen(it).toFen() } ?: BoardPosition.START_FEN
                require(sourceInitial == game.initialFen) { "Source initial position metadata disagrees" }
                require(Pgn.export(parsed) == game.canonicalPgn && parsed.tags == Pgn.canonicalTags(game.tags, parsed.initialPosition, game.result) &&
                    parsed.result == game.result) { "Canonical score/metadata differs from source normalization" }
                require(parsed.initialPosition.toFen() == game.initialFen && parsed.line.plies.map { it.move.uci } == game.uci &&
                    parsed.line.plies.map { it.san } == game.san && path.last().toFen() == game.finalFen)
                require(game.tags["White"] == game.white.name && game.tags["Black"] == game.black.name && game.annotationStatus == "NOT_INCLUDED")
                val match = requireNotNull(taxonomy).identify(path)
                require(game.openingIds == match?.openings?.map { it.id }.orEmpty() &&
                    game.openingMatchedAtPly == match?.matchedAtPly) { "Game taxonomy references differ from its required pack." }
                locations(game.occurrences)
                records += ContentRecordEntity(m.packId, game.id, "GAME", "${game.white.name} — ${game.black.name}",
                    BoardPosition.fromFen(game.finalFen).positionKey, json.encodeToString(game))
                positions += path.mapIndexed { ply, p -> PositionEntity(m.packId, game.id, ply, p.positionKey) }
            }
            require(records.map { it.recordId }.distinct().size == records.size) { "Duplicate content IDs." }
            issues.forEach { locations(listOf(it.location)) }
            val c = m.coverage
            require(c.acceptedRecords == records.size && c.duplicates == issues.count { it.kind == IssueKind.DUPLICATE } &&
                c.quarantined == issues.count { it.kind != IssueKind.DUPLICATE } && c.inputRecords == records.size + issues.size) { "Coverage disposition counts disagree." }
            val depths = openings.map { it.uci.size } + games.map { it.uci.size }
            require(c.minPlies == (depths.minOrNull() ?: 0) && c.maxPlies == (depths.maxOrNull() ?: 0))
            if (m.source.kind == SourceKind.OPENING_TAXONOMY) {
                require(c.families == openings.map { it.family }.distinct().size && c.distinctNames == openings.map { it.name }.distinct().size &&
                    c.distinctPositions == openings.map { it.positionKey }.distinct().size &&
                    c.transposedPositions == openings.groupBy { it.positionKey }.count { (_, routes) -> routes.map { it.uci }.distinct().size > 1 })
            } else {
                require(c.gamePlies == games.sumOf { it.uci.size } && c.distinctPositions == positions.map { it.positionKey }.distinct().size &&
                    c.gamesMatchedToTaxonomy == games.count { it.openingIds.isNotEmpty() } &&
                    c.unresolvedPlayers == games.flatMap { listOf(it.white, it.black) }.distinctBy { it.id }.count { it.fideId == null })
            }
            val notices = text("ATTRIBUTION.txt")
            require(notices.isNotBlank() && notices.length <= 65_536)
            return Validated(records, positions, games, notices)
        }
    }
}

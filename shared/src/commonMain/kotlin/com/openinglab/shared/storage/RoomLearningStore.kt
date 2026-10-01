package com.openinglab.shared.storage

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.chess.san
import com.openinglab.shared.content.*
import com.openinglab.shared.model.ChessMove
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.map
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
    private val dao = database.learningDao()
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
            val validated = withContext(Dispatchers.Default) { validatePayloads(bundle, manifest) }
            // Packs are immutable/retained. Do expensive naming checks before holding the writer;
            // the commit below rechecks dependency hashes atomically.
            if (manifest.source.kind == SourceKind.BROADCAST_GAMES) {
                require(manifest.dependencies.size == 1) { "Game pack must declare its taxonomy dependency." }
                val dependency = manifest.dependencies.single()
                require(dao.packById(dependency.packId)?.manifestHash == dependency.manifestSha256) {
                    "Install the exact required opening pack first."
                }
                val taxonomyPayloads = dao.recordsInPack(dependency.packId, "OPENING")
                withContext(Dispatchers.Default) {
                    val index = SourcedOpeningIndex(taxonomyPayloads.map { json.decodeFromString<OpeningRecord>(it) })
                    for (game in validated.games) {
                        currentCoroutineContext().ensureActive()
                        val match = index.identify(Pgn.parse(game.canonicalPgn).positions())
                        require(game.openingIds == match?.openings?.map { it.id }.orEmpty() &&
                            game.openingMatchedAtPly == match?.matchedAtPly) { "Game taxonomy references differ from its required pack." }
                    }
                }
            }
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
    override suspend fun openings(packId: String?) = (if (packId == null) dao.records("OPENING") else dao.recordsInPack(packId, "OPENING"))
        .map { json.decodeFromString<OpeningRecord>(it) }
    override suspend fun openingIdsAt(positionKey: String) = dao.openingIdsAt(positionKey)
    override suspend fun game(id: String) = dao.game(id)?.let { json.decodeFromString<GameRecord>(it) }
    override suspend fun latestBookmark() = dao.latestBookmark()?.let { json.decodeFromString<LessonBookmark>(it.payload) }
    override suspend fun saveBookmark(bookmark: LessonBookmark) {
        require(bookmark.replay.branches.size <= com.openinglab.shared.lesson.LessonReplay.MAX_BRANCH_DEPTH)
        require(bookmark.mistakes >= 0 && bookmark.assisted >= 0 && bookmark.contentVersion.matches(hashPattern))
        dao.bookmark(BookmarkEntity(bookmark.lessonId, json.encodeToString(bookmark), now()))
    }
    override suspend fun recordAttempt(attempt: LearningAttempt) = dao.attempt(
        AttemptEntity(attempt.id, attempt.lessonId, json.encodeToString(attempt), attempt.recordedAt))
    override suspend fun attempts(lessonId: String) = dao.attempts(lessonId).map { json.decodeFromString<LearningAttempt>(it) }
    override suspend fun selectRepertoire(selection: RepertoireSelection) = dao.repertoire(
        RepertoireEntity(selection.lessonId, json.encodeToString(selection)))
    override suspend fun repertoires() = dao.repertoires().map { json.decodeFromString<RepertoireSelection>(it) }

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

        private suspend fun validatePayloads(bundle: PackBundle, m: ContentManifest): Validated {
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
                require(Pgn.export(parsed) == game.canonicalPgn && parsed.tags == game.tags && parsed.result == game.result)
                require(parsed.initialPosition.toFen() == game.initialFen && parsed.line.plies.map { it.move.uci } == game.uci &&
                    parsed.line.plies.map { it.san } == game.san && parsed.positions().last().toFen() == game.finalFen)
                require(game.tags["White"] == game.white.name && game.tags["Black"] == game.black.name && game.annotationStatus == "NOT_INCLUDED")
                locations(game.occurrences)
                records += ContentRecordEntity(m.packId, game.id, "GAME", "${game.white.name} — ${game.black.name}",
                    BoardPosition.fromFen(game.finalFen).positionKey, json.encodeToString(game))
                positions += parsed.positions().mapIndexed { ply, p -> PositionEntity(m.packId, game.id, ply, p.positionKey) }
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

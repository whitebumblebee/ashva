package com.openinglab.shared.content

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.lesson.LessonGraph
import kotlinx.serialization.Serializable

@Serializable enum class SourceKind { OPENING_TAXONOMY, BROADCAST_GAMES }
@Serializable enum class InputFormat { OPENINGS_TSV, PGN_ZSTD, LICENSE }
@Serializable data class SourceFile(val name: String, val url: String, val format: InputFormat)
@Serializable data class ContentSource(
    val id: String, val kind: SourceKind, val title: String, val url: String, val revision: String,
    val license: String, val licenseUrl: String, val licenseEvidenceUrl: String,
    val attribution: String, val rightsReviewedOn: String, val redistributionApproved: Boolean,
    val modifications: String, val coverage: String, val files: List<SourceFile>,
)
@Serializable data class SourceConfiguration(val schemaVersion: Int, val sources: List<ContentSource>)
@Serializable data class SnapshotFile(
    val path: String, val url: String, val bytes: Long, val sha256: String,
    val decodedPath: String? = null, val decodedBytes: Long? = null, val decodedSha256: String? = null,
)
@Serializable data class SnapshotLock(val schemaVersion: Int, val configSha256: String, val retrievedAt: String, val files: List<SnapshotFile>)
@Serializable data class SourceLocation(val sourceId: String, val file: String, val ordinal: Int, val rawSha256: String)
@Serializable data class OpeningRecord(
    val id: String, val eco: String, val name: String, val family: String,
    val uci: List<String>, val san: List<String>, val finalFen: String, val positionKey: String,
    val occurrences: List<SourceLocation>,
)
/** Without a verified FIDE ID, identity is source-scoped; names/usernames are not guessed aliases. */
@Serializable data class PlayerReference(val id: String, val name: String, val fideId: String? = null, val identityStatus: String)
@Serializable data class GameRecord(
    val id: String, val externalIds: List<String>, val white: PlayerReference, val black: PlayerReference,
    val tags: Map<String, String>, val result: String, val initialFen: String,
    val uci: List<String>, val san: List<String>, val finalFen: String,
    val canonicalPgn: String, val openingIds: List<String>, val openingMatchedAtPly: Int?,
    val occurrences: List<SourceLocation>, val annotationStatus: String = "NOT_INCLUDED",
) {
    /** Legal scores, not invented commentary. The recorded original line remains immutable. */
    fun toLessonGraph(): LessonGraph {
        val game = Pgn.parse(canonicalPgn)
        require(game.initialPosition.toFen() == initialFen && game.result == result)
        require(game.line.plies.map { it.move.uci } == uci && game.line.plies.map { it.san } == san)
        require(game.positions().last().toFen() == finalFen)
        return LessonGraph.fromPgn(game, id)
    }
}
@Serializable enum class IssueKind { INVALID, INCOMPLETE, UNSUPPORTED, CONFLICT, DUPLICATE }
@Serializable data class ImportIssue(val location: SourceLocation, val kind: IssueKind, val detail: String, val retainedId: String? = null)
@Serializable data class CoverageCounts(
    val inputRecords: Int, val acceptedRecords: Int, val duplicates: Int, val quarantined: Int,
    val families: Int = 0, val distinctNames: Int = 0, val distinctPositions: Int = 0,
    val transposedPositions: Int = 0, val minPlies: Int = 0, val maxPlies: Int = 0,
    val gamePlies: Int = 0, val gamesMatchedToTaxonomy: Int = 0, val unresolvedPlayers: Int = 0,
)
@Serializable data class PackFile(val name: String, val bytes: Long, val sha256: String)
@Serializable data class PackDependency(val packId: String, val manifestSha256: String)
@Serializable data class ContentManifest(
    val schemaVersion: Int = 1, val processorVersion: String = "opening-lab-import/1",
    val packId: String, val source: ContentSource, val retrievedAt: String,
    val inputs: List<SnapshotFile>, val coverage: CoverageCounts, val files: List<PackFile>,
    val limitations: List<String>,
    val snapshotLockSha256: String,
    val dependencies: List<PackDependency> = emptyList(),
)

data class SourcedOpeningMatch(val openings: List<OpeningRecord>, val matchedAtPly: Int, val atCurrentPosition: Boolean)

/** Full-source names index; no dependence on the seven authored teaching openings. */
class SourcedOpeningIndex(openings: List<OpeningRecord>) {
    private val byPosition = openings.groupBy { it.positionKey }
    fun at(position: BoardPosition): List<OpeningRecord> = byPosition[position.positionKey].orEmpty()
    fun identify(positions: List<BoardPosition>): SourcedOpeningMatch? {
        require(positions.isNotEmpty())
        for (i in positions.indices.reversed()) {
            val matches = at(positions[i])
            if (matches.isNotEmpty()) return SourcedOpeningMatch(matches, i, i == positions.lastIndex)
        }
        return null
    }
}

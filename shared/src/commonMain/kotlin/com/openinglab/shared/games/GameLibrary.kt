// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.games

import com.openinglab.shared.chess.*
import com.openinglab.shared.content.*
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.InstalledPack
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable

/** Learner-only data; never inserted into licensed packs or public observed-game populations. */
@Serializable
data class PrivateGameRecord(
    val id: String, val canonicalPgn: String, val tags: Map<String, String>,
    val white: PlayerReference, val black: PlayerReference, val result: String,
    val initialFen: String, val finalFen: String, val uci: List<String>, val san: List<String>,
) {
    fun checkedGame(): PgnGame {
        require(id == "private-game:" + contentSha256(canonicalPgn.encodeToByteArray()))
        require(canonicalPgn.encodeToByteArray().size <= MAX_BYTES)
        val parsed = Pgn.parse(canonicalPgn)
        require(parsed.tags == Pgn.canonicalTags(tags, parsed.initialPosition, result))
        require(parsed.initialPosition.toFen() == initialFen && parsed.result == result)
        require(parsed.line.plies.map { it.move.uci } == uci && parsed.line.plies.map { it.san } == san)
        require(uci.size in 1..MAX_PLIES && parsed.positions().last().toFen() == finalFen)
        require(white == privatePlayer(tags["White"] ?: "?") && black == privatePlayer(tags["Black"] ?: "?"))
        require(Pgn.export(parsed) == canonicalPgn)
        return parsed
    }

    companion object {
        const val MAX_BYTES = 256 * 1024
        const val MAX_PLIES = 4096
        fun import(text: String): PrivateGameRecord {
            require(text.encodeToByteArray().size <= MAX_BYTES) { "Private PGN exceeds the 256 KiB single-game limit" }
            val parsed = Pgn.parse(text) // Archive imports fail explicitly; no other games are silently dropped.
            require(parsed.line.plies.size in 1..MAX_PLIES)
            val canonical = Pgn.export(parsed)
            require(canonical.encodeToByteArray().size <= MAX_BYTES)
            // Building checks recursive variation/total-ply bounds before storage.
            com.openinglab.shared.lesson.LessonGraph.fromPgn(parsed)
            return PrivateGameRecord("private-game:" + contentSha256(canonical.encodeToByteArray()), canonical,
                parsed.tags, privatePlayer(parsed.tags["White"] ?: "?"), privatePlayer(parsed.tags["Black"] ?: "?"),
                parsed.result, parsed.initialPosition.toFen(), parsed.positions().last().toFen(),
                parsed.line.plies.map { it.move.uci }, parsed.line.plies.map { it.san })
        }
        private fun privatePlayer(name: String) = PlayerReference("private-player:" + contentSha256(name.trim().lowercase().encodeToByteArray()),
            name, identityStatus = "USER_SUPPLIED_UNVERIFIED") // Never aliases a public/FIDE source identity.
    }
}

@Serializable
data class FollowedPlayer(val id: String, val names: List<String>, val identityStatus: String, val fideId: String? = null) {
    fun validate() {
        require(id.length in 1..160 && names.size in 1..128 && names.distinct().size == names.size)
        require(names.all { it.isNotBlank() && it.length <= 1024 && it.none(Char::isISOControl) })
        require(identityStatus in setOf("SOURCE_SCOPED_UNVERIFIED", "FIDE_ID_REPORTED_BY_SOURCE", "USER_SUPPLIED_UNVERIFIED"))
        if (fideId != null) require(fideId.matches(Regex("[1-9][0-9]{0,9}")) && id == "fide-$fideId" && identityStatus == "FIDE_ID_REPORTED_BY_SOURCE")
        else require(identityStatus != "FIDE_ID_REPORTED_BY_SOURCE")
    }
}

data class LibraryPack(val installed: InstalledPack, val games: List<GameRecord>, val openingNames: Map<String, String>)
data class LibraryPlayer(val reference: FollowedPlayer, val scoreIds: Set<String>, val reportedTitles: Set<String>)

sealed interface LibraryScore {
    val id: String
    val white: PlayerReference
    val black: PlayerReference
    val tags: Map<String, String>
    val result: String
    val san: List<String>
    val canonicalPgn: String
    val openingNames: List<String>

    data class Broadcast(val game: GameRecord, val origins: List<InstalledPack>, override val openingNames: List<String>) : LibraryScore {
        override val id get() = "broadcast:${game.id}"
        override val white get() = game.white
        override val black get() = game.black
        override val tags get() = game.tags
        override val result get() = game.result
        override val san get() = game.san
        override val canonicalPgn get() = game.canonicalPgn
    }
    data class Private(val game: PrivateGameRecord) : LibraryScore {
        override val id get() = game.id
        override val white get() = game.white
        override val black get() = game.black
        override val tags get() = game.tags
        override val result get() = game.result
        override val san get() = game.san
        override val canonicalPgn get() = game.canonicalPgn
        override val openingNames get() = listOfNotNull(tags["Opening"]?.takeIf { it != "?" })
    }
    val year: Int? get() = (tags["Date"]?.takeIf { it.take(4).toIntOrNull() != null } ?: tags["UTCDate"])?.take(4)?.toIntOrNull()
    val event: String get() = tags["Event"] ?: "?"
}

data class GameLibraryFilter(
    val query: String = "", val playerId: String? = null, val event: String = "", val year: Int? = null,
    val opening: String = "", val playerColor: PieceColor? = null, val result: String? = null,
    val followedOnly: Boolean = false, val reportedGmOnly: Boolean = false,
) {
    init {
        require(query.length <= 240 && event.length <= 240 && opening.length <= 240)
        require(result == null || result in setOf("1-0", "0-1", "1/2-1/2", "*"))
        require(year == null || year in 1000..9999)
    }
}

/** Metadata/search index only: legal source validation happened during atomic installation. */
class GameLibrary private constructor(val scores: List<LibraryScore>, val players: List<LibraryPlayer>, val packs: List<InstalledPack>) {
    private val byId = scores.associateBy { it.id }
    fun score(id: String) = byId[id]
    fun search(filter: GameLibraryFilter, followedIds: Set<String> = emptySet(), checkpoint: () -> Unit = {}): List<LibraryScore> {
        val terms = normalize(filter.query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        return scores.filter { score ->
            checkpoint()
            val ids = setOf(score.white.id, score.black.id)
            val selected = filter.playerId
            val text = normalize(listOf(score.white.name, score.black.name, score.event, score.tags["Date"].orEmpty(), score.tags["UTCDate"].orEmpty(),
                score.tags["ECO"].orEmpty(), score.openingNames.joinToString(" "), score.result).joinToString(" "))
            terms.all { it in text } &&
                (selected == null || selected in ids) &&
                (!filter.followedOnly || ids.any { it in followedIds }) &&
                (filter.event.isBlank() || normalize(filter.event) in normalize(score.event)) &&
                (filter.year == null || score.year == filter.year) &&
                (filter.opening.isBlank() || normalize(filter.opening) in normalize(score.openingNames.joinToString(" ") + " " + score.tags["ECO"].orEmpty())) &&
                (filter.result == null || filter.result == score.result) &&
                (filter.playerColor == null || (selected != null && (if (filter.playerColor == PieceColor.WHITE) score.white else score.black).id == selected)) &&
                (!filter.reportedGmOnly || listOf("WhiteTitle", "BlackTitle").any { score.tags[it] == "GM" })
        }
    }

    companion object {
        const val MAX_SCORES = 50_000
        fun build(packs: List<LibraryPack>, privateGames: List<PrivateGameRecord> = emptyList(), checkpoint: () -> Unit = {}): GameLibrary {
            require(packs.size <= 32 && packs.map { it.installed.manifest.packId }.distinct().size == packs.size)
            require(packs.sumOf { it.games.size } + privateGames.size <= MAX_SCORES)
            val broadcasts = linkedMapOf<String, LibraryScore.Broadcast>()
            packs.sortedBy { it.installed.manifest.packId }.forEach { pack ->
                checkpoint()
                require(pack.installed.manifest.source.kind == SourceKind.BROADCAST_GAMES)
                require(pack.games.size == pack.installed.manifest.coverage.acceptedRecords)
                require(pack.games.map { it.id }.distinct().size == pack.games.size)
                pack.games.forEach { game ->
                    checkpoint()
                    val names = game.openingIds.map { requireNotNull(pack.openingNames[it]) { "Required retained taxonomy name is unavailable" } }.distinct().sorted()
                    val old = broadcasts[game.id]
                    if (old == null) broadcasts[game.id] = LibraryScore.Broadcast(game, listOf(pack.installed), names)
                    else {
                        // Same stable ID counts once only when its score/metadata agree. Never guess real-game duplicates.
                        require(old.game.copy(occurrences = emptyList(), openingIds = emptyList(), openingMatchedAtPly = null) ==
                            game.copy(occurrences = emptyList(), openingIds = emptyList(), openingMatchedAtPly = null)) { "Conflicting game identity" }
                        broadcasts[game.id] = old.copy(origins = old.origins + pack.installed, openingNames = (old.openingNames + names).distinct().sorted())
                    }
                }
            }
            require(privateGames.map { it.id }.distinct().size == privateGames.size)
            val scores = (broadcasts.values + privateGames.map { LibraryScore.Private(it) }).sortedWith(
                compareByDescending<LibraryScore> { it.year ?: 0 }.thenBy { it.id })
            data class PlayerBuilder(val fideId: String?, val status: String, val names: MutableSet<String>, val scoreIds: MutableSet<String>, val titles: MutableSet<String>)
            val players = linkedMapOf<String, PlayerBuilder>()
            scores.forEach { score ->
                checkpoint()
                for ((side, ref) in listOf("White" to score.white, "Black" to score.black)) {
                    val entry = players.getOrPut(ref.id) { PlayerBuilder(ref.fideId, ref.identityStatus, linkedSetOf(), linkedSetOf(), linkedSetOf()) }
                    require(entry.fideId == ref.fideId && entry.status == ref.identityStatus) { "Conflicting player identity" }
                    entry.names += ref.name; entry.scoreIds += score.id
                    score.tags["${side}Title"]?.takeIf { it.isNotBlank() && it != "?" }?.let { entry.titles += it }
                }
            }
            val shelf = players.map { (id, p) ->
                val follow = FollowedPlayer(id, p.names.sorted(), p.status, p.fideId).also { it.validate() }
                LibraryPlayer(follow, p.scoreIds.toSet(), p.titles.toSet())
            }.sortedBy { normalize(it.reference.names.first()) }
            return GameLibrary(scores, shelf, packs.map { it.installed })
        }
        private fun normalize(text: String) = text.lowercase().replace('ó', 'o').replace('é', 'e').replace('ü', 'u').trim()
    }
}

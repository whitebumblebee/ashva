// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.content

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.sanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoirePolicy
import com.openinglab.shared.storage.InstalledPack

data class ObservedGameSample(val source: InstalledPack, val games: List<GameRecord>)
data class ObservedReply(val uci: String, val san: String, val scores: Int)
data class ObservedPositionReplies(
    val scoresSeen: Int,
    val scoresWithReply: Int,
    val replies: List<ObservedReply>,
) {
    val scoresWithoutReply: Int get() = scoresSeen - scoresWithReply
    fun count(uci: String): Int = replies.firstOrNull { it.uci == uci }?.scores ?: 0
}
data class ObservedChoiceCounts(val selected: Int, val notSelected: Int, val outsideSnapshot: Int)

/**
 * Original score observations, not master-population statistics or move evaluations.
 * One vote per stable score ID and normalized position, using its FIRST visit only.
 * Transpositions share a position, but clocks/repetition history and human identities do not.
 * Repeated source IDs must agree on the score; different IDs are not guessed duplicate games.
 * Retains exact manifests, licenses, dependencies and hashes. Never changes a lesson/policy.
 */
class ObservedReplyIndex private constructor(
    val sources: List<InstalledPack>,
    val totalScores: Int,
    private val positions: Map<String, ObservedPositionReplies>,
) {
    fun at(positionKey: String): ObservedPositionReplies = positions[positionKey]
        ?: ObservedPositionReplies(0, 0, emptyList())

    fun choices(policy: RepertoirePolicy, positionKey: String, recordedMoves: Set<String>): ObservedChoiceCounts {
        val turn = positionKey.split(' ').getOrNull(1)
        require(turn in listOf("w", "b"))
        val ownTurn = turn == if (policy.side == PieceColor.WHITE) "w" else "b"
        val selected = if (ownTurn) setOfNotNull(policy.preferredMoves[positionKey]) else policy.opponentReplies[positionKey].orEmpty().toSet()
        require(selected.all { it in recordedMoves }) { "Policy choices are outside the supplied snapshot" }
        val replies = at(positionKey).replies
        return ObservedChoiceCounts(
            replies.filter { it.uci in selected }.sumOf { it.scores },
            replies.filter { it.uci in recordedMoves && it.uci !in selected }.sumOf { it.scores },
            replies.filter { it.uci !in recordedMoves }.sumOf { it.scores },
        )
    }

    companion object {
        const val MAX_SCORES = 10_000
        const val MAX_TOTAL_PLIES = 500_000

        /** Call on a worker; checkpoints allow platform cancellation without Android dependencies. */
        fun build(samples: List<ObservedGameSample>, checkpoint: () -> Unit = {}): ObservedReplyIndex {
            require(samples.isNotEmpty()) { "No installed game sample" }
            require(samples.size <= 32 && samples.map { it.source.manifest.packId }.distinct().size == samples.size)
            require(samples.sumOf { it.games.size.toLong() } <= MAX_SCORES)
            require(samples.sumOf { sample -> sample.games.sumOf { it.uci.size.toLong() } } <= MAX_TOTAL_PLIES)
            val unique = linkedMapOf<String, GameRecord>()
            for (sample in samples) {
                checkpoint()
                val manifest = sample.source.manifest
                require(manifest.source.kind == SourceKind.BROADCAST_GAMES && manifest.source.redistributionApproved)
                require(sample.source.manifestSha256.matches(Regex("[a-f0-9]{64}")))
                require(manifest.coverage.acceptedRecords == sample.games.size)
                require(sample.games.map { it.id }.distinct().size == sample.games.size)
                require(manifest.coverage.gamePlies.toLong() == sample.games.sumOf { it.uci.size.toLong() })
                for (game in sample.games) {
                    require(game.id.isNotBlank() && game.id.length <= 512)
                    val old = unique[game.id]
                    if (old != null) require(old.initialFen == game.initialFen && old.uci == game.uci && old.san == game.san && old.finalFen == game.finalFen && old.result == game.result) {
                        "Conflicting score identity"
                    } else unique[game.id] = game
                }
            }
            class Counts {
                var seen = 0
                val replies = linkedMapOf<String, Pair<String, Int>>()
            }
            val counts = linkedMapOf<String, Counts>()
            for (game in unique.values) {
                checkpoint()
                require(game.uci.size in 1..SourceValidation.MAX_GAME_PLIES && game.uci.size == game.san.size)
                require(game.result in setOf("1-0", "0-1", "1/2-1/2"))
                var board = BoardPosition.fromFen(game.initialFen)
                val visited = mutableSetOf<String>()
                for (ply in 0..game.uci.size) {
                    checkpoint()
                    val key = board.positionKey
                    val firstVisit = visited.add(key)
                    val entry = if (firstVisit) counts.getOrPut(key) { Counts() }.also { it.seen++ } else null
                    if (ply == game.uci.size) break
                    val move = ChessMove.fromUci(game.uci[ply])
                    val transition = board.sanAndPlay(move)
                    val san = transition.san
                    require(san == game.san[ply]) { "Score notation mismatch" }
                    if (entry != null) {
                        val old = entry.replies[move.uci]
                        require(old == null || old.first == san)
                        entry.replies[move.uci] = san to ((old?.second ?: 0) + 1)
                    }
                    board = transition.position
                }
                require(board.toFen() == game.finalFen) { "Score endpoint mismatch" }
            }
            return ObservedReplyIndex(samples.map { it.source }, unique.size, counts.mapValues { (_, value) ->
                val replies = value.replies.map { (uci, count) -> ObservedReply(uci, count.first, count.second) }
                    .sortedWith(compareByDescending<ObservedReply> { it.scores }.thenBy { it.uci })
                ObservedPositionReplies(value.seen, replies.sumOf { it.scores }, replies)
            })
        }
    }
}

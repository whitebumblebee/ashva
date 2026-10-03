// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.review

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.lesson.AttemptKind
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoireBook
import com.openinglab.shared.repertoire.RepertoirePolicy
import com.openinglab.shared.repertoire.RepertoirePolicyRef
import com.openinglab.shared.storage.LearningAttempt
import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Exact history, not just a transposed board: repeated positions can have different draw context.
 * Common identical prefixes share a card inside one immutable course. No cross-course inference. */
@Serializable data class RecallTarget(
    val id: String, val lessonId: String, val contentVersion: String, val pathId: String,
    val title: String, val initialFen: String, val prefix: List<String>, val expectedUci: String,
    val side: PieceColor,
) {
    val ply: Int get() = prefix.size
    fun identity(): String = contentSha256(Json.encodeToString(listOf(lessonId, contentVersion,
        initialFen, side.name) + prefix + listOf("next", expectedUci)).encodeToByteArray())
    fun validate(checkpoint: () -> Unit = {}) {
        require(lessonId.length in 1..512 && pathId.length in 1..512 && title.length in 1..600)
        require(contentVersion.matches(Regex("[a-f0-9]{64}")) && prefix.size <= 512 && id == identity())
        var board = BoardPosition.fromFen(initialFen)
        prefix.forEach { checkpoint(); board = board.apply(ChessMove.fromUci(it)) }
        require(board.sideToMove == side && board.isLegal(ChessMove.fromUci(expectedUci)))
    }
}

@Serializable data class RecallContext(val policy: RepertoirePolicyRef? = null, val pathId: String? = null)
@Serializable data class RecallScope(
    val id: String, val groupId: String, val title: String,
    val setId: String? = null, val setRevision: Int? = null,
) {
    fun validate() {
        require(id.matches(Regex("[a-f0-9]{64}")) && groupId.length in 1..600 && title.length in 1..600)
        require((setId == null) == (setRevision == null))
        require(setRevision == null || setRevision >= 0)
    }
}
data class RecallEntry(val target: RecallTarget, val context: RecallContext = RecallContext())
data class RecallEnrollment(val scope: RecallScope, val entries: List<RecallEntry>) {
    fun validate(checkpoint: () -> Unit = {}) {
        scope.validate()
        require(entries.size in 1..MAX_CARDS && entries.map { it.target.id }.distinct().size == entries.size)
        require(entries.sumOf { it.target.prefix.size.toLong() } <= 200_000) { "Review history exceeds local limits; nothing omitted" }
        entries.forEach { checkpoint(); it.target.validate(checkpoint); require(it.context.policy?.revision?.let { r -> r >= 0 } != false) }
        require(scope.id == scopeIdentity(scope.groupId, entries, scope.setRevision))
    }
    companion object { const val MAX_CARDS = 10_000 }
}

fun scopeIdentity(groupId: String, entries: List<RecallEntry>, setRevision: Int? = null): String =
    contentSha256(Json.encodeToString(listOf(groupId, setRevision?.toString().orEmpty()) + entries
        .sortedBy { it.target.id }.map { "${it.target.id}:${it.context.policy?.id}:${it.context.policy?.revision}" }).encodeToByteArray())

/** The denominator is the learner's admitted finite routes, never the theoretical opening tree. */
object RecallPlanner {
    fun entries(book: RepertoireBook, side: PieceColor, pathIds: List<String>, policy: RepertoirePolicy? = null,
        checkpoint: () -> Unit = {}): List<RecallEntry> {
        if (policy != null) {
            book.validate(policy)
            require(side == policy.side && pathIds.toSet() == book.coverage(policy, checkpoint).eligiblePathIds.toSet())
        }
        val unique = linkedMapOf<String, RecallEntry>()
        pathIds.sorted().forEach { id ->
            val path = book.graph.paths.getValue(id)
            path.moves.forEachIndexed { ply, move ->
                checkpoint()
                if (path.positions[ply].sideToMove == side) {
                    val raw = RecallTarget("", book.graph.id, book.contentVersion, id,
                        "${book.graph.title} · ${path.name}", book.graph.initialPosition.toFen(),
                        path.moves.take(ply).map { it.move.uci }, move.move.uci, side)
                    val target = raw.copy(id = raw.identity())
                    unique.getOrPut(target.id) { RecallEntry(target, RecallContext(policy?.let { RepertoirePolicyRef(it.id, it.revision) }, id)) }
                    require(unique.size <= RecallEnrollment.MAX_CARDS) { "Review scope exceeds local limits; no decisions were omitted" }
                }
            }
        }
        require(unique.isNotEmpty()) { "This chosen scope has no learner decisions" }
        return unique.values.toList()
    }
    fun route(book: RepertoireBook, side: PieceColor, pathId: String): RecallEnrollment {
        val entries = entries(book, side, listOf(pathId))
        val group = "route:${book.graph.id}:$pathId:${side.name}"
        return RecallEnrollment(RecallScope(scopeIdentity(group, entries), group,
            "${book.graph.title} · ${book.graph.paths.getValue(pathId).name} · ${side.name.lowercase()}"), entries)
    }
    fun policy(book: RepertoireBook, policy: RepertoirePolicy, checkpoint: () -> Unit = {}): RecallEnrollment {
        val entries = entries(book, policy.side, book.coverage(policy, checkpoint).eligiblePathIds, policy, checkpoint)
        val group = "policy:${policy.id}"
        return RecallEnrollment(RecallScope(scopeIdentity(group, entries), group, "${policy.name} · revision ${policy.revision}"), entries)
    }
    fun target(book: RepertoireBook, side: PieceColor, pathId: String, ply: Int): RecallTarget =
        entries(book, side, listOf(pathId)).single { it.target.ply == ply }.target
}

@Serializable enum class RecallGrade { UNAIDED, ASSISTED, NOT_RECALLED }
@Serializable enum class RecallHelp { HINT, AUTOMATIC_HINT, ENGINE, STUDY, BRANCH }
@Serializable data class RecallEvent(
    val attempt: LearningAttempt, val target: RecallTarget, val grade: RecallGrade,
    val help: Set<RecallHelp> = emptySet(), val scopeId: String,
) {
    fun validate() {
        target.validate()
        require(attempt.lessonId == target.lessonId && attempt.ply == target.ply && attempt.side == target.side.name)
        require(attempt.recordedAt in 0..RecallScheduler.MAX_TIME && attempt.id.length in 1..160)
        ChessMove.fromUci(attempt.moveUci)
        require(attempt.outcome in AttemptKind.entries.map { it.name })
        require((grade == RecallGrade.UNAIDED) == (!attempt.assisted && help.isEmpty() && attempt.outcome == "EXPECTED"))
        require(grade != RecallGrade.ASSISTED || (attempt.assisted && attempt.outcome == "EXPECTED"))
        require(grade != RecallGrade.NOT_RECALLED || attempt.outcome != "EXPECTED")
        require(attempt.outcome != "EXPECTED" || attempt.moveUci == target.expectedUci)
    }
}

@Serializable data class RecallState(
    val schedulerVersion: Int = 1, val dueAt: Long = 0, val lastAt: Long? = null,
    val spacedSuccesses: Int = 0, val unaidedAttempts: Int = 0, val assistedAttempts: Int = 0,
    val notRecalledAttempts: Int = 0,
)
/** A transparent conservative schedule, not an empirically calibrated probability of mastery.
 * Only due, separated, unaided answers extend intervals. Early drills never inflate progress. */
object RecallScheduler {
    const val DAY = 86_400_000L
    const val RETRY = 600_000L
    const val MAX_TIME = 4_102_444_800_000L // 2100-01-01; bounded arithmetic and broken clock refusal.
    val intervalsDays = listOf(1, 3, 7, 14, 30, 60, 120, 240)
    fun grade(old: RecallState, grade: RecallGrade, at: Long): RecallState {
        require(old.schedulerVersion == 1 && at in 0..MAX_TIME && old.dueAt in 0..MAX_TIME)
        require(old.spacedSuccesses in 0..intervalsDays.size && listOf(old.unaidedAttempts, old.assistedAttempts, old.notRecalledAttempts).all { it in 0 until Int.MAX_VALUE })
        // Preserve the event, but do not schedule from out-of-order or backwards-clock work.
        if (old.lastAt != null && at < old.lastAt) return old
        val counts = old.copy(lastAt = at,
            unaidedAttempts = old.unaidedAttempts + if (grade == RecallGrade.UNAIDED) 1 else 0,
            assistedAttempts = old.assistedAttempts + if (grade == RecallGrade.ASSISTED) 1 else 0,
            notRecalledAttempts = old.notRecalledAttempts + if (grade == RecallGrade.NOT_RECALLED) 1 else 0)
        if (grade != RecallGrade.UNAIDED) return counts.copy(spacedSuccesses = 0, dueAt = (at + RETRY).coerceAtMost(MAX_TIME))
        if (at < old.dueAt || (old.lastAt != null && at - old.lastAt < RETRY)) return counts
        val successes = (old.spacedSuccesses + 1).coerceAtMost(intervalsDays.size)
        return counts.copy(spacedSuccesses = successes, dueAt = (at + intervalsDays[successes - 1] * DAY).coerceAtMost(MAX_TIME))
    }
}

data class RecallCard(val target: RecallTarget, val state: RecallState, val context: RecallContext)
data class RecallScopeSummary(val scope: RecallScope, val total: Int, val introduced: Int, val established: Int, val due: Int, val nextDueAt: Long?)
data class LearningTotals(val attempts: Int = 0, val unaided: Int = 0, val assisted: Int = 0, val notRecalled: Int = 0,
    val legacyUngraded: Int = 0, val studyViews: Int = 0)

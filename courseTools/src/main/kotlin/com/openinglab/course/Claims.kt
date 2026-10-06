// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.chess.parseSanAndPlay
import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.model.PieceType
import kotlinx.serialization.Serializable

/** Phase 7: a typed claim with a machine-checkable payload. Prose is shown only when the claim passes. */
@Serializable
data class Claim(
    val type: String,
    val text: String,
    val side: String? = null,
    /** SAN moves referenced by the claim (meaning depends on type). */
    val moves: List<String> = emptyList(),
    /** For PREVENTS: the opponent move that is stopped. For STATISTIC: the move the statistic is about. */
    val target: String? = null,
    /** For PREVENTS: the mover's natural alternative after which [target] would be good. */
    val alternative: String? = null,
    /** For MISTAKE: "before" (instead of this node's move) or "after" (as the reply to it). */
    val from: String = "after",
    /** For EVALUATION: WHITE_BETTER, BLACK_BETTER, WHITE_SLIGHTLY, BLACK_SLIGHTLY, EQUAL. */
    val result: String? = null,
    /** For STRUCTURE: facts such as "doubled:black:c", "open:e", "bishop-pair:black", "queens-off". */
    val facts: List<String> = emptyList(),
    /** For STATISTIC: MOST_COMMON, SHARE_ABOVE:<0..1>, SCORES_ABOVE:<0..1> and population club|master. */
    val stat: String? = null,
    val population: String = "club",
)

@Serializable
data class NodeWriting(
    val title: String? = null,
    val claims: List<Claim> = emptyList(),
    val principle: Claim? = null,
    val glossary: List<String> = emptyList(),
    val whitePlan: List<Claim> = emptyList(),
    val blackPlan: List<Claim> = emptyList(),
)

@Serializable
data class ChapterWriting(
    val writer: String,
    val intro: List<Claim> = emptyList(),
    /** Keyed by the node's SAN path from the start, e.g. "e4 e5 Nf3 Nc6 Bb5 Nf6 O-O". */
    val nodes: Map<String, NodeWriting> = emptyMap(),
    val glossary: List<GlossaryWriting> = emptyList(),
    /** Keyed by variation name from the table of contents: introduction and each side's winning plans. */
    val variations: Map<String, VariationWriting> = emptyMap(),
)

@Serializable
data class VariationWriting(val intro: List<Claim> = emptyList(), val white: List<Claim> = emptyList(), val black: List<Claim> = emptyList())

@Serializable
data class GlossaryWriting(val id: String, val title: String, val claims: List<Claim>)

@Serializable
data class CheckedClaim(val claim: Claim, val passed: Boolean, val evidence: String, val reason: String, val auto: Boolean)

/** Everything the checker may consult about one tree edge. */
class ClaimContext(
    val before: Line,
    val node: TreeNode,
    /** Moves (UCI) along the course's own continuation from this node (main children first), for PLAN/PREPARES. */
    val continuation: List<String>,
    val club: PositionIndex,
    val master: PositionIndex,
) {
    val after: Line get() = before + node.uci
    val mover: PieceColor get() = before.board.sideToMove
}

class ClaimChecker(private val tools: EngineTools) {
    private val sanToken = Regex("(?<![A-Za-z0-9])(?:\\.\\.\\.)?(O-O-O|O-O|[KQRBN][a-h]?[1-8]?x?[a-h][1-8](?:=[QRBN])?[+#]?|[a-h]x[a-h][1-8](?:=[QRBN])?[+#]?|[a-h][1-8](?:=[QRBN])?[+#]?)(?![A-Za-z0-9])")

    /** Tokens that look like moves or squares must all be backed by the claim's own checked payload. */
    fun unsupportedTokens(claim: Claim, nodeSan: String): List<String> {
        val payload = (claim.moves + listOfNotNull(claim.target, claim.alternative, nodeSan) + claim.facts).joinToString(" ")
        val allowed = if (claim.type == "IDEA") nodeSan else payload
        return sanToken.findAll(claim.text).map { it.groupValues[1] }.filter { token ->
            val bare = token.trimEnd('+', '#')
            !allowed.contains(bare)
        }.toList()
    }

    fun check(claim: Claim, ctx: ClaimContext, auto: Boolean = false): CheckedClaim = checkInner(claim, ctx, auto).copy(auto = auto)

    private fun checkInner(claim: Claim, ctx: ClaimContext, auto: Boolean): CheckedClaim {
        fun fail(reason: String) = CheckedClaim(claim, false, "", reason, auto)
        fun pass(evidence: String) = CheckedClaim(claim, true, evidence, "", auto)
        if (claim.text.isBlank() || claim.text.length > 600) return fail("text length")
        val unsupported = unsupportedTokens(claim, ctx.node.san)
        if (unsupported.isNotEmpty()) return fail("text mentions unsupported moves/squares: $unsupported")
        return try {
            when (claim.type) {
                "IDEA" -> pass("generated explanation")
                "THREAT" -> {
                    val threat = tools.threat(ctx.after, 100) ?: return fail("engine finds no threat")
                    if (claim.moves.firstOrNull()?.let { same(it, threat.san) } == true) pass("engine-checked") else fail("engine threat is ${threat.san}")
                }
                "PREVENTS" -> checkPrevents(claim, ctx)
                "PREPARES" -> {
                    val follow = claim.moves.singleOrNull() ?: return fail("PREPARES needs one move")
                    when {
                        inSequence(ctx.after, enginePv(ctx.after), listOf(follow), ctx.mover) -> pass("engine-checked")
                        inSequence(ctx.after, ctx.continuation, listOf(follow), ctx.mover) -> pass("course line")
                        else -> fail("$follow is not played by ${ctx.mover} in the engine line or the course continuation")
                    }
                }
                "PLAN" -> {
                    val side = PieceColor.valueOf(claim.side ?: return fail("PLAN needs side"))
                    if (claim.moves.isEmpty()) return fail("PLAN needs moves")
                    when {
                        inSequence(ctx.after, enginePv(ctx.after, 24), claim.moves, side) -> pass("engine-checked")
                        inSequence(ctx.after, ctx.continuation, claim.moves, side) -> pass("course line")
                        else -> fail("plan moves not found in order in engine line or course continuation")
                    }
                }
                "MISTAKE" -> {
                    val base = if (claim.from == "before") ctx.before else ctx.after
                    val bad = claim.moves.firstOrNull() ?: return fail("MISTAKE needs the bad move")
                    val move = base.board.parseSanAndPlay(bad).move.uci
                    val check = tools.compare(base, move)
                    if (check.lossCp < 100) return fail("engine loss only ${check.lossCp}cp")
                    val refutation = claim.moves.drop(1)
                    if (refutation.isNotEmpty()) {
                        val afterBad = base + move
                        val first = afterBad.board.parseSanAndPlay(refutation.first()).move.uci
                        val r = tools.compare(afterBad, first)
                        if (r.lossCp > 40) return fail("refutation ${refutation.first()} is not the engine's answer")
                        var b = afterBad.board
                        refutation.forEach { b = b.parseSanAndPlay(it).position } // legal sequence
                    }
                    pass("engine-checked")
                }
                "EVALUATION" -> {
                    val e = tools.whiteEval(ctx.after) ?: return fail("no evaluation")
                    val ok = when (claim.result) {
                        "WHITE_BETTER" -> e >= 100; "BLACK_BETTER" -> e <= -100
                        "WHITE_SLIGHTLY" -> e in 25..149; "BLACK_SLIGHTLY" -> e in -149..-25
                        "EQUAL" -> kotlin.math.abs(e) <= 50; else -> false
                    }
                    if (ok) pass("engine-checked") else fail("engine evaluation $e does not match ${claim.result}")
                }
                "STRUCTURE" -> {
                    if (claim.facts.isEmpty()) return fail("STRUCTURE needs facts")
                    val wrong = claim.facts.filterNot { structureFact(ctx.after.board, it) }
                    if (wrong.isEmpty()) pass("board fact") else fail("not true on the board: $wrong")
                }
                "STATISTIC" -> checkStatistic(claim, ctx)
                else -> fail("unknown claim type ${claim.type}")
            }
        } catch (e: IllegalArgumentException) { fail("illegal or invalid payload: ${e.message?.take(120)}") }
    }

    private fun same(a: String, b: String) = a.trimEnd('+', '#') == b.trimEnd('+', '#')

    private fun checkPrevents(claim: Claim, ctx: ClaimContext): CheckedClaim {
        fun fail(r: String) = CheckedClaim(claim, false, "", r, false)
        val target = claim.target ?: return fail("PREVENTS needs target")
        val afterBoard = ctx.after.board
        // After the move the target must be illegal for a still-present piece, or clearly bad.
        val targetAfter = runCatching { afterBoard.parseSanAndPlay(target).move.uci }.getOrNull()
        if (targetAfter != null) {
            val check = tools.compare(ctx.after, targetAfter)
            if (check.lossCp < 120) return fail("$target is still playable after the move (loss ${check.lossCp}cp)")
        }
        val alternative = claim.alternative
        if (alternative != null) {
            val alt = ctx.before + ctx.before.board.parseSanAndPlay(alternative).move.uci
            val targetAlt = alt.board.parseSanAndPlay(target).move.uci
            val check = tools.compare(alt, targetAlt)
            return if (check.lossCp <= 40) CheckedClaim(claim, true, "engine-checked", "", false)
            else fail("after $alternative, $target is not a good move either (loss ${check.lossCp}cp)")
        }
        val prevented = tools.prevents(ctx.before, ctx.node.uci)
        return if (prevented.any { same(it.san, target) }) CheckedClaim(claim, true, "engine-checked", "", false)
        else fail("engine null-move probe does not show $target as prevented")
    }

    private fun checkStatistic(claim: Claim, ctx: ClaimContext): CheckedClaim {
        fun fail(r: String) = CheckedClaim(claim, false, "", r, false)
        val index = if (claim.population == "master") ctx.master else ctx.club
        // The statistic is about the node's own move (target = node SAN) or about a reply after it.
        val (line, target) = when (claim.target) {
            null, ctx.node.san -> ctx.before to ctx.node.san
            else -> ctx.after to claim.target
        }
        val uci = line.board.parseSanAndPlay(target).move.uci
        val moves = index.movesAt(line.board.positionKey)
        val total = moves.values.sumOf { it.games }
        val s = moves[uci] ?: return fail("no games with $target")
        if (total < 20) return fail("only $total games at this position")
        val stat = claim.stat ?: return fail("STATISTIC needs stat")
        val ok = when {
            stat == "MOST_COMMON" -> moves.maxBy { it.value.games }.key == uci
            stat.startsWith("SHARE_ABOVE:") -> s.games.toDouble() / total >= stat.substringAfter(':').toDouble()
            stat.startsWith("SCORES_ABOVE:") -> s.games >= 20 && s.scoreFor(line.board.sideToMove == PieceColor.WHITE) >= stat.substringAfter(':').toDouble()
            else -> false
        }
        return if (ok) CheckedClaim(claim, true, "game statistics", "", false) else fail("statistic does not hold ($stat)")
    }

    private fun enginePv(line: Line, plies: Int = 16): List<String> = tools.analyse(line).best?.pv?.take(plies).orEmpty()

    /** True when [sans] are played in order by [side] along [ucis] from [start] (other moves may intervene). */
    fun inSequence(start: Line, ucis: List<String>, sans: List<String>, side: PieceColor): Boolean {
        var board = start.board
        var i = 0
        for (uci in ucis) {
            if (i == sans.size) break
            val move = ChessMove.fromUci(uci)
            if (!board.isLegal(move)) return false
            val wanted = sans[i]
            if (board.sideToMove == side && runCatching { board.parseSanAndPlay(wanted).move == move }.getOrDefault(false)) i++
            board = board.apply(move)
        }
        return i == sans.size
    }

    companion object {
        fun structureFact(board: BoardPosition, fact: String): Boolean {
            val parts = fact.split(':')
            fun side(s: String) = PieceColor.valueOf(s.uppercase())
            val pawns = { c: PieceColor -> board.pieces.filterValues { it.color == c && it.type == PieceType.PAWN }.keys }
            return when (parts[0]) {
                "doubled" -> pawns(side(parts[1])).count { it[0] == parts[2][0] } >= 2
                "isolated" -> { val p = pawns(side(parts[1])); parts[2] in p && p.none { kotlin.math.abs(it[0] - parts[2][0]) == 1 } }
                "open" -> board.pieces.none { (s, p) -> s[0] == parts[1][0] && p.type == PieceType.PAWN }
                "halfopen" -> pawns(side(parts[1])).none { it[0] == parts[2][0] } && pawns(side(parts[1]).opposite).any { it[0] == parts[2][0] }
                "bishop-pair" -> board.pieces.values.count { it.color == side(parts[1]) && it.type == PieceType.BISHOP } == 2 &&
                    board.pieces.values.count { it.color == side(parts[1]).opposite && it.type == PieceType.BISHOP } < 2
                "queens-off" -> board.pieces.values.none { it.type == PieceType.QUEEN }
                "no-castling" -> board.toFen().split(' ')[2].let { rights -> if (side(parts[1]) == PieceColor.WHITE) rights.none { it in "KQ" } else rights.none { it in "kq" } }
                "majority" -> {
                    val files = if (parts[2] == "kingside") "efgh" else "abcd"
                    pawns(side(parts[1])).count { it[0] in files } > pawns(side(parts[1]).opposite).count { it[0] in files }
                }
                "pawn" -> board.pieceAt(parts[2]) == com.openinglab.shared.model.Piece(side(parts[1]), PieceType.PAWN)
                "piece" -> board.pieceAt(parts[3]) == com.openinglab.shared.model.Piece(side(parts[1]),
                    mapOf("K" to PieceType.KING, "Q" to PieceType.QUEEN, "R" to PieceType.ROOK, "B" to PieceType.BISHOP, "N" to PieceType.KNIGHT, "P" to PieceType.PAWN).getValue(parts[2]))
                "passed" -> {
                    val c = side(parts[1]); val sq = parts[2]
                    sq in pawns(c) && pawns(c.opposite).none { e -> kotlin.math.abs(e[0] - sq[0]) <= 1 && (if (c == PieceColor.WHITE) e[1] > sq[1] else e[1] < sq[1]) }
                }
                else -> false
            }
        }
    }
}

package com.openinglab.shared.content

import com.openinglab.shared.chess.Pgn
import com.openinglab.shared.chess.PgnGame
import com.openinglab.shared.chess.PgnLine
import com.openinglab.shared.chess.PositionStatus

object SourceValidation {
    const val MAX_GAME_CHARS = 256 * 1024
    const val MAX_GAME_PLIES = 1000

    fun opening(eco: String, name: String, moves: String): PgnGame {
        require(eco.matches(Regex("[A-E][0-9]{2}"))) { "Invalid ECO code" }
        require(name.isNotBlank() && name.length <= 500 && name.none { it.isISOControl() }) { "Invalid opening name" }
        require(moves.length in 1..16_384) { "Opening move text exceeds limits" }
        val game = Pgn.parse("$moves *")
        require(game.line.plies.size in 1..128) { "Opening must contain 1–128 half-moves" }
        require(game.tags.isEmpty() && game.line.leadingComments.isEmpty() && game.trailingComments.isEmpty()) { "Expected opening move text only" }
        require(game.line.plies.all { it.variations.isEmpty() && it.comments.isEmpty() && it.nags.isEmpty() }) { "Expected unannotated canonical opening line" }
        return game
    }

    fun game(raw: String): PgnGame {
        require(raw.length <= MAX_GAME_CHARS) { "Game exceeds 256 KiB character limit" }
        val game = Pgn.parse(raw)
        require(game.result != "*") { "Incomplete game: result is unknown" }
        require(game.line.plies.size in 1..MAX_GAME_PLIES) { "Game must contain 1–1000 main-line half-moves" }
        require(listOf("White", "Black", "Event").all { !game.tags[it].isNullOrBlank() && game.tags[it] != "?" }) { "Missing game/player metadata" }
        val final = game.positions().last()
        if (final.status() == PositionStatus.CHECKMATE) {
            val expected = if (final.sideToMove == com.openinglab.shared.model.PieceColor.WHITE) "0-1" else "1-0"
            require(game.result == expected) { "Recorded result conflicts with checkmate" }
        }
        // Do not treat source evals/NAGs, clock comments or hypothetical lines as reviewed teaching.
        val unannotated = PgnLine(game.line.plies.map { it.copy(comments = emptyList(), nags = emptyList(), variations = emptyList()) }, result = game.result)
        return game.copy(line = unannotated, trailingComments = emptyList())
    }
}

/** Tagged archive framing respects tag strings, brace/semicolon comments and RAV nesting.
 * A corrupt unclosed comment/RAV is quarantined as one tail frame, never guessed into valid games.
 */
object PgnFrames {
    fun split(text: String): List<String> {
        require(text.length <= 8 * 1024 * 1024) { "Split large archives into at most 8 MiB chunks" }
        val starts = mutableListOf(0)
        var brace = false; var semicolon = false; var tag = false; var quote = false; var escaped = false
        var rav = 0; var lineLeading = true; var movetext = false
        for (i in text.indices) {
            val c = text[i]
            if (semicolon) {
                if (c in "\r\n") { semicolon = false; lineLeading = true }
                continue
            }
            if (brace) { if (c == '}') brace = false; continue }
            if (tag) {
                if (escaped) escaped = false
                else if (quote && c == '\\') escaped = true
                else if (c == '"') quote = !quote
                else if (!quote && c == ']') tag = false
                continue
            }
            if (c in "\r\n") { lineLeading = true; continue }
            if (c.isWhitespace() || c == '\uFEFF') continue
            if (c == '[') {
                if (lineLeading && movetext && rav == 0) {
                    starts += i; movetext = false
                    require(starts.size <= 10_000) { "Too many archive records" }
                }
                tag = true
            } else when (c) {
                '{' -> brace = true
                ';', '%' -> semicolon = true
                '(' -> { rav++; movetext = true }
                ')' -> { rav = (rav - 1).coerceAtLeast(0); movetext = true }
                else -> movetext = true
            }
            lineLeading = false
        }
        return starts.mapIndexed { i, start -> text.substring(start, starts.getOrNull(i + 1) ?: text.length).trim() }.filter { it.isNotEmpty() }
    }
}

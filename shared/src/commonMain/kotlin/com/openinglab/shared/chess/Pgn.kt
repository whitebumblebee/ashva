package com.openinglab.shared.chess

import com.openinglab.shared.model.ChessMove
import com.openinglab.shared.model.PieceColor

data class PgnPly(
    val move: ChessMove,
    val san: String,
    val comments: List<String> = emptyList(),
    val nags: List<Int> = emptyList(),
    /** Each RAV replaces this move and starts at its pre-move position. */
    val variations: List<PgnLine> = emptyList(),
)

data class PgnLine(
    val plies: List<PgnPly>,
    val leadingComments: List<String> = emptyList(),
    val result: String? = null,
)

data class PgnGame(
    val tags: Map<String, String>,
    val initialPosition: BoardPosition,
    val line: PgnLine,
    val result: String,
    val trailingComments: List<String> = emptyList(),
) {
    private val replayedPositions: List<BoardPosition> by lazy {
        val positions = mutableListOf(initialPosition)
        for (ply in line.plies) positions += positions.last().apply(ply.move)
        positions.toList()
    }
    /** Immutable game values share one checked replay, not repeated full-board reconstruction. */
    fun positions(): List<BoardPosition> = replayedPositions
}

/** Bounded, legal-move-validated standard PGN import. Archives are parsed as separate games. */
object Pgn {
    private val tagNamePattern = Regex("[A-Za-z0-9_]+")
    private val moveNumberPattern = Regex("^\\d+\\.{1,3}")
    private val glyphPattern = Regex("[!?]+$")
    private val results = setOf("1-0", "0-1", "1/2-1/2", "*")
    private val glyphs = mapOf("!" to 1, "?" to 2, "!!" to 3, "??" to 4, "!?" to 5, "?!" to 6)
    private val roster = listOf("Event", "Site", "Date", "Round", "White", "Black", "Result")

    fun parse(text: String): PgnGame = parseArchive(text).also {
        require(it.size == 1) { "Expected one PGN game, found ${it.size}" }
    }.single()

    fun parseArchive(text: String): List<PgnGame> {
        require(text.length <= 16 * 1024 * 1024) { "PGN input exceeds the character limit; import the archive in chunks" }
        val parser = Parser(tokenize(text))
        val games = mutableListOf<PgnGame>()
        while (!parser.finished) games += parser.game()
        return games.toList()
    }

    /** Canonical PGN supplies unknown roster placeholders; source metadata itself remains unchanged. */
    fun canonicalTags(sourceTags: Map<String, String>, initialPosition: BoardPosition, result: String): Map<String, String> {
        require(result in results) { "Invalid result" }
        val tags = sourceTags.toMutableMap()
        if (initialPosition.toFen() != BoardPosition.START_FEN) {
            tags["SetUp"] = "1"
            tags["FEN"] = initialPosition.toFen()
        } else {
            tags.remove("SetUp")
            tags.remove("FEN")
        }
        roster.forEach { if (it !in tags) tags[it] = if (it == "Date") "????.??.??" else "?" }
        tags["Result"] = result
        return tags.toMap()
    }

    fun export(game: PgnGame): String {
        val tags = canonicalTags(game.tags, game.initialPosition, game.result)
        return buildString {
            for (key in roster + tags.keys.filterNot { it in roster }.sorted()) {
                require(tagNamePattern.matches(key)) { "Invalid tag name" }
                val value = tags.getValue(key).replace("\\", "\\\\").replace("\"", "\\\"")
                append("[$key \"$value\"]\n")
            }
            append('\n')
            append(exportLine(game.line, game.initialPosition, 0))
            append(' ')
            append(game.result)
            for (comment in game.trailingComments) {
                require('{' !in comment && '}' !in comment) { "PGN comment contains brace delimiter" }
                append(" {$comment}")
            }
            append('\n')
        }
    }

    private fun exportLine(line: PgnLine, initial: BoardPosition, depth: Int): String {
        require(depth <= 64) { "PGN variations nested too deeply" }
        fun comment(text: String): String {
            require('{' !in text && '}' !in text) { "PGN comment contains brace delimiter" }
            return "{$text}"
        }
        val output = line.leadingComments.map(::comment).toMutableList()
        var board = initial
        for (ply in line.plies) {
            val before = board
            val transition = board.sanAndPlay(ply.move)
            output += if (board.sideToMove == PieceColor.WHITE) "${board.fullmoveNumber}." else "${board.fullmoveNumber}..."
            output += transition.san
            ply.nags.forEach { require(it in 0..255); output += "$$it" }
            output += ply.comments.map(::comment)
            for (variation in ply.variations) {
                output += "(" + exportLine(variation, before, depth + 1) +
                    (variation.result?.let { " $it" } ?: "") + ")"
            }
            board = transition.position
        }
        return output.joinToString(" ")
    }

    private enum class Kind { TAG, COMMENT, OPEN, CLOSE, NAG, SYMBOL }
    private data class Token(val kind: Kind, val value: String, val offset: Int, val tagValue: String = "")

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = if (text.startsWith('\uFEFF')) 1 else 0
        while (i < text.length) {
            val start = i
            when (text[i]) {
                ' ', '\t', '\r', '\n' -> i++
                '%' -> {
                    require(i == 0 || text[i - 1] == '\n' || text[i - 1] == '\r') { "Escape line must start a line at $i" }
                    while (i < text.length && text[i] != '\n') i++
                }
                ';' -> {
                    i++
                    val begin = i
                    while (i < text.length && text[i] !in "\r\n") i++
                    tokens += Token(Kind.COMMENT, text.substring(begin, i).trim(), start)
                }
                '{' -> {
                    val end = text.indexOf('}', i + 1)
                    require(end >= 0) { "Unterminated comment at $i" }
                    val body = text.substring(i + 1, end)
                    require('{' !in body) { "Nested brace comment at $i" }
                    tokens += Token(Kind.COMMENT, body.trim(), start)
                    i = end + 1
                }
                '(' -> tokens += Token(Kind.OPEN, "(", i++)
                ')' -> tokens += Token(Kind.CLOSE, ")", i++)
                '[' -> {
                    i++
                    while (i < text.length && text[i].isWhitespace()) i++
                    val nameStart = i
                    while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
                    val name = text.substring(nameStart, i)
                    require(name.isNotEmpty()) { "Missing tag name at $start" }
                    while (i < text.length && text[i].isWhitespace()) i++
                    require(i < text.length && text[i++] == '"') { "Missing tag quote at $start" }
                    val value = buildString {
                        while (i < text.length && text[i] != '"') {
                            val c = text[i++]
                            if (c == '\\') {
                                require(i < text.length && text[i] in "\\\"") { "Invalid tag escape at $i" }
                                append(text[i++])
                            } else append(c)
                        }
                    }
                    require(i < text.length && text[i++] == '"') { "Unterminated tag at $start" }
                    while (i < text.length && text[i].isWhitespace()) i++
                    require(i < text.length && text[i++] == ']') { "Missing tag closing bracket at $start" }
                    tokens += Token(Kind.TAG, name, start, value)
                }
                '$' -> {
                    i++
                    val numberStart = i
                    while (i < text.length && text[i].isDigit()) i++
                    val value = text.substring(numberStart, i)
                    require(value.isNotEmpty()) { "Missing NAG at $start" }
                    tokens += Token(Kind.NAG, value, start)
                }
                else -> {
                    while (i < text.length && !text[i].isWhitespace() && text[i] !in "{}();[]$") i++
                    require(i > start) { "Unexpected '${text[i]}' at $i" }
                    tokens += Token(Kind.SYMBOL, text.substring(start, i), start)
                }
            }
            require(tokens.size <= 200_000) { "Too many PGN tokens" }
        }
        return tokens.toList()
    }

    private class Parser(private val tokens: List<Token>) {
        private var cursor = 0
        private var plyCount = 0
        val finished: Boolean get() = cursor >= tokens.size

        fun game(): PgnGame {
            val tags = mutableMapOf<String, String>()
            val headerComments = mutableListOf<String>()
            while (!finished && tokens[cursor].kind in setOf(Kind.TAG, Kind.COMMENT)) {
                val token = tokens[cursor++]
                if (token.kind == Kind.COMMENT) headerComments += token.value else
                    require(tags.put(token.value, token.tagValue) == null) { "Duplicate tag ${token.value}" }
            }
            val variant = tags["Variant"]
            require(variant == null || variant.lowercase() in setOf("standard", "chess", "normal")) { "Unsupported chess variant: $variant" }
            require(tags["SetUp"] == null || tags["SetUp"] in setOf("0", "1")) { "Invalid SetUp tag" }
            require(tags["SetUp"] != "1" || tags["FEN"] != null) { "SetUp requires FEN" }
            require(tags["FEN"] == null || tags["SetUp"] == "1") { "FEN requires SetUp 1" }
            val initial = tags["FEN"]?.let(BoardPosition::fromFen) ?: BoardPosition.starting()
            val parsedLine = line(initial, 0)
            val line = parsedLine.copy(leadingComments = headerComments + parsedLine.leadingComments)
            val result = line.result ?: throw IllegalArgumentException("Missing PGN termination marker")
            require(tags["Result"] == null || tags["Result"] == result) { "Result tag and movetext disagree" }
            val trailing = mutableListOf<String>()
            while (!finished && tokens[cursor].kind == Kind.COMMENT) trailing += tokens[cursor++].value
            return PgnGame(tags.toMap(), initial, line, result, trailing.toList())
        }

        private fun line(initial: BoardPosition, depth: Int): PgnLine {
            require(depth <= 64) { "PGN variations nested too deeply" }
            var board = initial
            var beforeLast = initial
            val moves = mutableListOf<PgnPly>()
            val leading = mutableListOf<String>()
            var result: String? = null
            fun annotate(transform: (PgnPly) -> PgnPly) {
                require(moves.isNotEmpty()) { "Annotation before a move at token $cursor" }
                moves[moves.lastIndex] = transform(moves.last())
            }
            while (!finished) {
                val token = tokens[cursor]
                if (token.kind == Kind.CLOSE) {
                    require(depth > 0) { "Unmatched variation close at ${token.offset}" }
                    cursor++
                    return PgnLine(moves.toList(), leading.toList(), result)
                }
                require(result == null || token.kind == Kind.COMMENT) { "Unexpected token after result at ${token.offset}" }
                cursor++
                when (token.kind) {
                    Kind.TAG -> throw IllegalArgumentException("Missing termination before tag at ${token.offset}")
                    Kind.COMMENT -> if (moves.isEmpty()) leading += token.value else annotate { it.copy(comments = it.comments + token.value) }
                    Kind.NAG -> {
                        val nag = token.value.toIntOrNull()
                        require(nag != null && nag in 0..255) { "Invalid NAG at ${token.offset}" }
                        annotate { it.copy(nags = it.nags + nag) }
                    }
                    Kind.OPEN -> {
                        require(moves.isNotEmpty()) { "Variation has no replaced move at ${token.offset}" }
                        val variation = line(beforeLast, depth + 1)
                        require(variation.plies.isNotEmpty()) { "Empty variation at ${token.offset}" }
                        annotate { it.copy(variations = it.variations + variation) }
                    }
                    Kind.CLOSE -> error("Handled before dispatch")
                    Kind.SYMBOL -> {
                        val symbol = token.value.replace(moveNumberPattern, "")
                        if (symbol.isEmpty() || symbol == "...") continue
                        if (symbol in results) {
                            result = symbol
                            if (depth == 0) return PgnLine(moves.toList(), leading.toList(), result)
                            continue
                        }
                        if (symbol == "e.p.") continue
                        val standaloneGlyph = glyphs[symbol]
                        if (standaloneGlyph != null) {
                            annotate { it.copy(nags = it.nags + standaloneGlyph) }
                            continue
                        }
                        require(++plyCount <= 100_000) { "Too many PGN moves" }
                        val transition = try { board.parseSanAndPlay(symbol) } catch (error: IllegalArgumentException) {
                            throw IllegalArgumentException("PGN move '$symbol' at offset ${token.offset}: ${error.message}")
                        }
                        beforeLast = board
                        val glyph = glyphPattern.find(symbol)?.value?.let(glyphs::get)
                        moves += PgnPly(transition.move, transition.san, nags = listOfNotNull(glyph))
                        board = transition.position
                    }
                }
            }
            require(depth == 0) { "Unterminated variation" }
            return PgnLine(moves.toList(), leading.toList(), result)
        }
    }
}

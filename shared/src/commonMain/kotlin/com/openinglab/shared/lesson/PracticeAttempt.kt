package com.openinglab.shared.lesson

import com.openinglab.shared.model.ChessMove

enum class AttemptKind { EXPECTED, AVAILABLE_BRANCH, LEGAL_OFF_LINE, ILLEGAL }
data class PracticeAttempt(val kind: AttemptKind, val expected: LessonMove, val branches: List<LessonBranch>)

/** Lesson recall is not engine evaluation: a legal different move is not called a blunder. */
fun LessonReplay.assess(move: ChessMove): PracticeAttempt {
    val expected = nextMove ?: throw IllegalArgumentException("Lesson is complete")
    val legal = position.isLegal(move)
    val alternatives = if (legal) branches().filter { it.nextMove.move == move } else emptyList()
    val kind = when {
        !legal -> AttemptKind.ILLEGAL
        expected.move == move -> AttemptKind.EXPECTED
        alternatives.isNotEmpty() -> AttemptKind.AVAILABLE_BRANCH
        else -> AttemptKind.LEGAL_OFF_LINE
    }
    return PracticeAttempt(kind, expected, alternatives)
}

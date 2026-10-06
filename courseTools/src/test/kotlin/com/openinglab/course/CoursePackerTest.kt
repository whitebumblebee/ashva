// SPDX-License-Identifier: Apache-2.0
package com.openinglab.course

import kotlin.test.Test
import kotlin.test.assertEquals

class CoursePackerTest {
    private fun claim(text: String, evidence: String, passed: Boolean = true, auto: Boolean = false, type: String = "IDEA") =
        CheckedClaim(Claim(type, text), passed, evidence, "", auto)

    @Test fun anchorIntroPrecedesOnlyPassedWriterClaimsAndMergesEvidence() {
        val intro = listOf(claim("Keep the centre flexible.", "game statistics"),
            claim("Develop before attacking.", "generated explanation"), claim("Rejected intro.", "engine-checked", passed = false))
        val claims = listOf(claim("The bishop has room.", "board fact"), claim("This is the course continuation.", "course line"),
            claim("Rejected node claim.", "game statistics", passed = false), claim("Automatic move prose.", "board fact", auto = true))
        val (text, label) = CoursePacker.explanation(claims, listOf("Ruy Lopez: Berlin Defense · 4...Bc5" to intro), "Fallback.")
        assertEquals("Berlin Defense · 4...Bc5: Keep the centre flexible. Develop before attacking. The bishop has room. This is the course continuation.", text)
        assertEquals("Generated · engine-checked · game statistics · board facts · general idea, not engine-checked", label)
    }

    @Test fun anchorWithNoNodeWritingShowsOnlyThePassedIntro() {
        val (text, label) = CoursePacker.explanation(listOf(claim("Automatic prose.", "engine-checked", auto = true)),
            listOf("Berlin Defense" to listOf(claim("Keep pieces active.", "generated explanation"))), "Fallback.")
        assertEquals("Berlin Defense: Keep pieces active.", text)
        assertEquals("Generated · general idea, not engine-checked", label)
    }

    @Test fun nonAnchorsAndRejectedIntrosKeepExistingMoveProseAndFallback() {
        val claims = listOf(claim("Written idea.", "generated explanation"), claim("Duplicate automatic idea.", "board fact", auto = true),
            claim("Automatic structure.", "board fact", auto = true, type = "STRUCTURE"),
            claim("Automatic plan.", "course line", auto = true, type = "PLAN"),
            claim("Extra automatic claim.", "game statistics", auto = true, type = "STATISTIC"),
            claim("Evaluation.", "engine-checked", auto = true, type = "EVALUATION"))
        val expected = "Written idea. Automatic structure. Automatic plan." to "Generated · engine-checked · board facts · general idea, not engine-checked"
        assertEquals(expected, CoursePacker.explanation(claims, emptyList(), "Fallback."))
        assertEquals(expected, CoursePacker.explanation(claims,
            listOf("Berlin Defense" to listOf(claim("Rejected intro.", "game statistics", passed = false))), "Fallback."))
        assertEquals("Fallback." to "Generated · board facts", CoursePacker.explanation(emptyList(), emptyList(), "Fallback."))
    }

    @Test fun sidesWithEnoughWinsButNoPatternsExplainTheVarietyOfPlans() {
        for (white in listOf(true, false)) {
            val who = if (white) "White" else "Black"
            for (wins in listOf(8, 25)) {
                val ideas = CoursePacker.sideIdeas(SideWins(wins, emptyList(), emptyList()), white,
                    listOf(claim("Keep pieces active.", "generated explanation"), claim("Rejected plan.", "board fact", passed = false)))
                assertEquals("Keep pieces active. In $wins master games $who won from here, but no single move stands out: the winners used many different plans. Replay the example games to see them.", ideas.text)
                assertEquals(wins, ideas.wins)
                assertEquals("Generated · game statistics · general idea, not engine-checked", ideas.label)
            }
            assertEquals("Only 7 decisive master wins for $who from here, too few to show a reliable pattern.",
                CoursePacker.sideIdeas(SideWins(7, emptyList(), emptyList()), white, emptyList()).text)
        }
    }

    @Test fun winningPatternTextUsesOriginalSanAndFallsBackForOlderPatterns() {
        val patterns = listOf(WinPattern("Bc6", 8, 10, 2, 10, san = "Bxc6"), WinPattern("Qd8", 5, 10, 0, 10, san = "Qxd8"),
            WinPattern("Nd2", 6, 10, 0, 10, san = "Nbd2"), WinPattern("h4", 5, 10, 0, 10), WinPattern("a4", 5, 10, 0, 10, san = " "))
        assertEquals("In 10 master games White won from here, the winners most distinctively played Bxc6 (80% of wins vs 20% of other games); Qxd8 (50% of wins vs 0% of other games); Nbd2 (60% of wins vs 0% of other games); h4 (50% of wins vs 0% of other games); a4 (50% of wins vs 0% of other games).",
            CoursePacker.sideIdeas(SideWins(10, patterns, emptyList()), true, emptyList()).text)
    }
}

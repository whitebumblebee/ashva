// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.chess

import kotlin.test.*

class PgnMetadataTest {
    @Test fun missingRosterFieldsGetUnknownCanonicalPlaceholdersWithoutMutatingTheSource() {
        val raw = "[Event \"Synthetic\"]\n[White \"A\"]\n[Black \"B\"]\n[Result \"1-0\"]\n\n1. e4 e5 1-0"
        val game = Pgn.parse(raw)
        val source = game.tags.toMap()
        val exported = Pgn.parse(Pgn.export(game))
        assertEquals(source, game.tags)
        assertFalse("Site" in game.tags); assertFalse("Date" in game.tags); assertFalse("Round" in game.tags)
        assertEquals("?", exported.tags["Site"]); assertEquals("????.??.??", exported.tags["Date"])
        assertEquals("?", exported.tags["Round"])
        assertEquals(Pgn.canonicalTags(source, game.initialPosition, game.result), exported.tags)
        assertNotEquals(Pgn.canonicalTags(source + ("Site" to "invented"), game.initialPosition, game.result), exported.tags)
        assertFailsWith<IllegalArgumentException> { Pgn.parse(raw.replace("[Result \"1-0\"]", "[Result \"0-1\"]")) }
    }
}

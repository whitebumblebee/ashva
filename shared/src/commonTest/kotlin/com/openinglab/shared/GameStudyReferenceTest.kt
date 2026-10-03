// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared

import com.openinglab.shared.games.*
import com.openinglab.shared.lesson.LessonGraph
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.storage.LessonBookmark
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import kotlin.test.*

class GameStudyReferenceTest {
    @Test fun identityIncludesExactSourceVersionAndSeparatesPrivateRecords() {
        val ref = GameStudyReference("record", "fixture-v1", "a".repeat(64))
        ref.validate()
        assertNotEquals(ref.lessonId, ref.copy(packId = "fixture-v2").lessonId)
        assertNotEquals(ref.lessonId, ref.copy(manifestSha256 = "b".repeat(64)).lessonId)
        assertNotEquals(ref.lessonId, ref.copy(recordId = "another").lessonId)
        assertFailsWith<IllegalArgumentException> { ref.copy(manifestSha256 = "unknown").validate() }
        assertFailsWith<IllegalArgumentException> { ref.copy(packId = "../source").validate() }
        assertFailsWith<IllegalArgumentException> { ref.copy(privateImport = true).validate() }
    }

    @Test fun gameBookmarksRoundTripAndLegacyJsonDefaultsRemainCompatible() {
        val record = PrivateGameRecord.import("1. e4 e5 *")
        val ref = GameStudyReference(record.id, privateImport = true).also { it.validate() }
        val graph = LessonGraph.fromPgn(record.checkedGame(), ref.lessonId)
        val bookmark = LessonBookmark(ref.lessonId, "a".repeat(64), graph.start(PieceColor.BLACK).jump(1).snapshot(), "STUDY", gameReference = ref)
        val json = Json { encodeDefaults = true }
        assertEquals(bookmark, json.decodeFromString<LessonBookmark>(json.encodeToString(bookmark)))
        val legacy = JsonObject(json.parseToJsonElement(json.encodeToString(bookmark.copy(gameReference = null))).jsonObject.filterKeys { it != "gameReference" })
        assertNull(json.decodeFromString<LessonBookmark>(legacy.toString()).gameReference)
    }
}

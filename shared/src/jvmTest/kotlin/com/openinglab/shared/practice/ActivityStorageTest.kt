// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.practice

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.openinglab.shared.storage.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class ActivityStorageTest {
    private fun fixture(test: suspend (Path, LearningDatabase, RoomLearningStore) -> Unit) = runBlocking {
        val directory = Files.createTempDirectory("ashva-activity-")
        val path = directory.resolve("learning.db")
        val db = createJvmLearningDatabase(path.toString())
        try { test(path, db, RoomLearningStore(db)) }
        finally { db.close(); Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun completedLinesAreImmutableIdempotentAndSurviveColdOpen() = fixture { path, db, store ->
        val event = StudyActivity("session", "retained-course", "retained-line", StudyActivity.PRACTICE, 1000)
        store.recordStudyActivity(event); store.recordStudyActivity(event)
        assertFails { store.recordStudyActivity(event.copy(recordedAt = 2000)) }
        store.recordStudyActivity(event.copy(id = "study", kind = StudyActivity.STUDY))
        val activity = store.learnerActivity(3000).first()
        assertEquals(2, activity.openings.size)
        assertEquals(1, routineProgress(StudyCalendar({ 3000 }) { 0 }, activity, emptyList(), RoutineSettings()).lines)
        db.close()
        val cold = createJvmLearningDatabase(path.toString())
        try { assertEquals(activity, RoomLearningStore(cold).learnerActivity(3000).first()) }
        finally { cold.close() }
    }
    @Test fun activityIncludesLegacyAttemptsStudyViewsReviewAndTacticsWithoutInferringLineCompletions() = fixture { _, db, store ->
        val dao = db.learningDao()
        dao.attempt(AttemptEntity("attempt", "course", "unparsed legacy payload", 1000))
        dao.studyView(StudyViewEntity("study", "scope", 2000))
        dao.recallEvent(RecallEventEntity("review", "card", "UNAIDED", 3000, "retained"))
        db.tacticsDao().attempt(TacticsAttemptEntity("woodpecker-easy", 1, "puzzle", 0, false, 200, 4000))
        val activity = store.learnerActivity(5000).first()
        assertEquals(listOf(1000L, 2000L, 3000L, 4000L), activity.timestamps.sorted())
        assertTrue(activity.openings.isEmpty())
        assertEquals(4, StudyCalendar({ 5000 }) { it / 1000 }.week(activity.timestamps).activeDays.size)
    }
    @Test fun dueCardsAreCountedOnceAcrossOverlappingActiveScopesAndIgnoreInactiveScopes() = fixture { _, db, store ->
        val dao = db.learningDao()
        listOf("shared", "later", "inactive").forEachIndexed { index, id ->
            dao.recallCard(RecallCardEntity(id, "retained", "retained", if (index == 1) 5000 else 1000, null, 0))
        }
        dao.activateRecallScope(ActiveRecallScopeEntity("one", "scope-one"))
        dao.activateRecallScope(ActiveRecallScopeEntity("two", "scope-two"))
        dao.recallMemberships(listOf(RecallScopeCardEntity("scope-one", "shared", "retained"),
            RecallScopeCardEntity("scope-two", "shared", "retained"), RecallScopeCardEntity("scope-one", "later", "retained"),
            RecallScopeCardEntity("old-scope", "inactive", "retained")))
        assertEquals(1, store.learnerActivity(2000).first().due)
        assertEquals(2, store.learnerActivity(5000).first().due)
    }
    @Test fun resumedPuzzleKeepsItsFirstActivityTimestampAndDoesNotDuplicateDailyProgress() = fixture { _, _, store ->
        val event = StudyActivity("puzzle:day:set:cycle:puzzle", "set", "1:puzzle", StudyActivity.PUZZLE, 1000)
        store.recordStudyActivity(event); store.recordStudyActivity(event.copy(recordedAt = 2000))
        val activity = store.learnerActivity(3000).first()
        assertEquals(1, activity.puzzleActivities.size)
        assertEquals(1000, activity.puzzleActivities.single().recordedAt)
        assertTrue(activity.openings.isEmpty())
        assertEquals(1, routineProgress(StudyCalendar({ 3000 }) { 0 }, activity, emptyList(), RoutineSettings()).puzzles)
    }
    @Test fun legacyRouteStudyUsesItsExactLineWhilePolicyViewsDoNotInventStudiedLines() = fixture { _, _, store ->
        val book = com.openinglab.shared.repertoire.RepertoireBook(com.openinglab.shared.lesson.LessonGraph.fromOpening(
            com.openinglab.shared.data.OpeningCatalog.byId("ruy-lopez")))
        val route = com.openinglab.shared.review.RecallPlanner.route(book, com.openinglab.shared.model.PieceColor.WHITE, "ruy-main")
        store.enrollRecall(route)
        store.recordStudyView("old-route-view", route.scope.id, 1000)
        val policy = book.seed(com.openinglab.shared.model.PieceColor.WHITE, "ruy-main")
        store.saveRepertoirePolicy(policy)
        val family = com.openinglab.shared.review.RecallPlanner.policy(book, policy)
        store.enrollRecall(family); store.recordStudyView("old-policy-view", family.scope.id, 2000)
        val activity = store.learnerActivity(3000).first()
        assertEquals(1, activity.openings.size)
        assertEquals("ruy-lopez", activity.openings.single().lessonId)
        assertEquals("ruy-main", activity.openings.single().pathId)
        assertEquals(StudyActivity.STUDY, activity.openings.single().kind)
        assertEquals(listOf(1000L, 2000L), activity.timestamps.sorted())
    }
    @Test fun migrationSevenToEightPreservesAll24TablesAndAddsAnEmptyActivityTable() = runBlocking {
        val directory = Files.createTempDirectory("ashva-activity-migration-")
        val path = directory.resolve("learning.db")
        try {
            val schema = Json.parseToJsonElement(Files.readString(Path.of("shared/schemas/com.openinglab.shared.storage.LearningDatabase/7.json")))
                .jsonObject.getValue("database").jsonObject
            val tables = schema.getValue("entities").jsonArray.map { it.jsonObject }
            val before = linkedMapOf<String, List<String>>()
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                tables.forEach { table ->
                    val name = table.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    table["indices"]?.jsonArray.orEmpty().forEach { index ->
                        connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    }
                    val fields = table.getValue("fields").jsonArray.map { it.jsonObject }
                    val columns = fields.map { it.getValue("columnName").jsonPrimitive.content }
                    connection.prepare("INSERT INTO `$name` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})").use { statement ->
                        fields.forEachIndexed { i, field ->
                            if (field.getValue("affinity").jsonPrimitive.content == "INTEGER") statement.bindLong(i + 1, 123)
                            else statement.bindText(i + 1, "retained-$name-${columns[i]}")
                        }; statement.step()
                    }
                    before[name] = connection.prepare("SELECT * FROM `$name`").use { statement ->
                        assertTrue(statement.step()); fields.indices.map { statement.getText(it) }
                    }
                }
                schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
                connection.execSQL("PRAGMA user_version = 7")
            }
            val db = createJvmLearningDatabase(path.toString())
            try { assertNotNull(db.learningDao().latestBookmark()) } finally { db.close() }
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                before.forEach { (name, values) ->
                    connection.prepare("SELECT * FROM `$name`").use { statement ->
                        assertTrue(statement.step()); assertEquals(values, values.indices.map { statement.getText(it) }, name)
                    }
                }
                connection.prepare("PRAGMA user_version").use { it.step(); assertEquals(8, it.getLong(0)) }
                connection.prepare("SELECT COUNT(*) FROM daily_activity").use { it.step(); assertEquals(0, it.getLong(0)) }
                // The additive DDL is safe when re-applied, with all rows still present.
                LearningDatabase.MIGRATION_7_8.migrate(connection)
            }
            assertEquals(24, before.size)
        } finally { Files.walk(directory).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.tactics

import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.openinglab.shared.storage.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class TacticsStorageTest {
    private fun database(test: suspend (Path, LearningDatabase, TacticsStore) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("ashva-tactics-test-")
        val path = dir.resolve("learning.db")
        val db = createJvmLearningDatabase(path.toString())
        try { test(path, db, RoomTacticsStore(db)) }
        finally { db.close(); Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    @Test fun firstAttemptsAreAtomicImmutableAndResumeAcrossColdOpen() = database { path, db, store ->
        val ids = listOf("a", "b")
        val first = store.startCycle("easy", 100)
        assertEquals(first, store.startCycle("easy", 200))
        store.checkpoint("easy", 1, 5000); store.checkpoint("easy", 1, 1000)
        val attempt = TacticsAttempt("easy", 1, "a", 0, true, 5000, 300)
        store.recordAttempt(attempt, ids, 5000); store.recordAttempt(attempt, ids, 5000)
        assertFails { store.recordAttempt(attempt.copy(correct = false), ids, 5000) }
        assertFails { store.recordAttempt(attempt.copy(puzzleId = "b", ordinal = 0), ids, 6000) }
        assertEquals(1, store.load().attempts.size)
        db.close()
        val cold = createJvmLearningDatabase(path.toString())
        try {
            val next = RoomTacticsStore(cold)
            assertEquals(1, Woodpecker.nextOrdinal(ids, next.load().attempts))
            next.recordAttempt(TacticsAttempt("easy", 1, "b", 1, false, 6000, 400), ids, 11_000)
            val history = next.load()
            assertEquals(400L, history.cycles.single().completedAt)
            assertEquals(11_000L, history.cycles.single().activeMs)
            assertEquals(.5, Woodpecker.stats(history.cycles.single(), history.attempts, 2, null).accuracy)
            assertEquals(2, next.startCycle("easy", 500).cycle)
        } finally { cold.close() }
    }
    @Test fun resetOnlyRemovesOneSetsHistoryAndRetainsCustomDefinitionsAndOpeningRows() = database { _, db, store ->
        val spec = CustomSetSpec("Mine", 800, 2600, size = 25, seed = 1)
        val custom = TacticsCustomSet("custom-fixture", spec, (1..25).map { "p$it" }, 100)
        store.createCustomSet(custom); store.createCustomSet(custom)
        assertFails { store.createCustomSet(custom.copy(spec = spec.copy(name = "Changed"))) }
        db.learningDao().bookmark(BookmarkEntity("retained", "unchanged", 20))
        store.startCycle(custom.id, 100); store.startCycle("other", 101)
        store.recordAttempt(TacticsAttempt(custom.id, 1, "p1", 0, false, 4000, 200), custom.puzzleIds, 4000)
        store.reset(custom.id)
        assertEquals(listOf(custom), store.load().customSets)
        assertEquals("other", store.load().cycles.single().setId)
        assertTrue(store.load().attempts.isEmpty())
        assertEquals("unchanged", db.learningDao().latestBookmark()!!.payload)
    }
    @Test fun migrationSixToSevenPreservesEveryLegacyTableRow() = runBlocking {
        val dir = Files.createTempDirectory("ashva-tactics-migration-")
        val path = dir.resolve("learning.db")
        try {
            val schema = Json.parseToJsonElement(Files.readString(Path.of("shared/schemas/com.openinglab.shared.storage.LearningDatabase/6.json")))
                .jsonObject.getValue("database").jsonObject
            val tables = schema.getValue("entities").jsonArray.map { it.jsonObject }
            val before = linkedMapOf<String, List<String>>()
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                tables.forEach { table -> connection.execSQL(table.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content)) }
                tables.forEach { table -> table["indices"]?.jsonArray.orEmpty().forEach { index ->
                    connection.execSQL(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table.getValue("tableName").jsonPrimitive.content))
                } }
                schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
                tables.forEach { table ->
                    val name = table.getValue("tableName").jsonPrimitive.content
                    val fields = table.getValue("fields").jsonArray.map { it.jsonObject }
                    val columns = fields.map { it.getValue("columnName").jsonPrimitive.content }
                    connection.prepare("INSERT INTO `$name` (${columns.joinToString { "`$it`" }}) VALUES (${columns.joinToString { "?" }})").use { statement ->
                        fields.forEachIndexed { i, field ->
                            if (field.getValue("affinity").jsonPrimitive.content == "INTEGER") statement.bindLong(i + 1, 123)
                            else statement.bindText(i + 1, "retained-$name-${columns[i]}")
                        }; statement.step()
                    }
                    before[name] = row(connection, name, fields.size)
                }
                connection.execSQL("PRAGMA user_version = 6")
            }
            val db = createJvmLearningDatabase(path.toString())
            try { assertTrue(RoomTacticsStore(db).load().cycles.isEmpty()) } finally { db.close() }
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                before.forEach { (name, values) -> assertEquals(values, row(connection, name, values.size), name) }
                connection.prepare("PRAGMA user_version").use { it.step(); assertEquals(8, it.getLong(0)) }
            }
            assertEquals(21, before.size)
        } finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }
    private fun row(connection: SQLiteConnection, table: String, columns: Int): List<String> =
        connection.prepare("SELECT * FROM `$table`").use { statement -> assertTrue(statement.step()); (0 until columns).map { statement.getText(it) } }
}

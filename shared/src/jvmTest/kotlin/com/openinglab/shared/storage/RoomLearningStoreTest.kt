package com.openinglab.shared.storage

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.openinglab.shared.content.*
import com.openinglab.shared.chess.BoardPosition
import com.openinglab.shared.lesson.*
import com.openinglab.shared.data.OpeningCatalog
import com.openinglab.shared.model.PieceColor
import com.openinglab.shared.repertoire.RepertoireBook
import com.openinglab.shared.repertoire.RepertoireSet
import com.openinglab.shared.repertoire.RepertoirePolicyRef
import com.openinglab.shared.games.*
import com.openinglab.shared.review.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class RoomLearningStoreTest {
    private val json = Json { encodeDefaults = true }
    private val taxonomyId = "lichess-openings-c67912be581f-import-v1"
    private val broadcastId = "lichess-broadcast-2020-04-2020-04-snap-import-v1"
    private val januaryId = "lichess-broadcast-2020-01-2020-01-snap-import-v1"
    private fun bundle(id: String): PackBundle {
        val path = Path.of("content/packs/$id")
        val manifest = Files.readAllBytes(path.resolve("manifest.json"))
        val m = json.decodeFromString<ContentManifest>(manifest.decodeToString())
        return PackBundle(manifest, m.files.associate { it.name to Files.readAllBytes(path.resolve(it.name)) }, contentSha256(manifest))
    }
    private fun revised(base: PackBundle, id: String, edit: (ContentManifest) -> ContentManifest = { it }): PackBundle {
        val manifest = edit(json.decodeFromString<ContentManifest>(base.manifest.decodeToString()).copy(packId = id))
        val bytes = json.encodeToString(manifest).encodeToByteArray()
        return base.copy(manifest = bytes, expectedManifestSha256 = contentSha256(bytes))
    }
    private fun small(): PackBundle {
        val base = bundle(taxonomyId)
        val row = base.files.getValue("openings.jsonl").decodeToString().lineSequence().first().plus("\n").encodeToByteArray()
        val record = json.decodeFromString<OpeningRecord>(row.decodeToString().trim())
        val files = base.files + ("openings.jsonl" to row)
        return revised(base.copy(files = files), "fixture-v1") { it.copy(
            files = it.files.map { file -> file.copy(bytes = files.getValue(file.name).size.toLong(), sha256 = contentSha256(files.getValue(file.name))) },
            coverage = CoverageCounts(1, 1, 0, 0, families = 1, distinctNames = 1, distinctPositions = 1,
                minPlies = record.uci.size, maxPlies = record.uci.size)) }
    }
    private fun bookmark(): LessonBookmark {
        val graph = LessonGraph.fromOpening(OpeningCatalog.openings.first())
        return LessonBookmark(graph.id, "a".repeat(64), graph.start(PieceColor.BLACK).jump(3).snapshot(), "STUDY", studied = true)
    }
    private fun database(test: suspend (Path, LearningDatabase, RoomLearningStore) -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("opening-lab-store-test-")
        val path = dir.resolve("learning.db")
        val db = createJvmLearningDatabase(path.toString())
        try { test(path, db, RoomLearningStore(db)) }
        finally { db.close(); Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
    }

    @Test fun emptyStoreAndLearnerStateSurviveColdReopen() = database { path, db, store ->
        assertTrue(store.activePacks().isEmpty())
        assertTrue(store.availability.first().isEmpty()) // catalog overlays MISSING, not a fabricated downloaded row
        val bookmark = bookmark()
        val attempt = LearningAttempt("attempt-1", bookmark.lessonId, bookmark.replay.rootPathId, 3, "BLACK", "a7a6", "EXPECTED", true, 123)
        val selection = RepertoireSelection(bookmark.lessonId, "BLACK", bookmark.replay.rootPathId)
        store.saveBookmark(bookmark); store.recordAttempt(attempt); store.selectRepertoire(selection)
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val next = RoomLearningStore(reopened)
            assertEquals(bookmark, next.latestBookmark())
            assertEquals(listOf(attempt), next.attempts(bookmark.lessonId))
            assertEquals(listOf(selection), next.repertoires())
        } finally { reopened.close() }
    }

    @Test fun wholeJanuarySnapshotPassesAtomicValidationAndColdReopensWithoutChangingLearnerRows() = database { path, db, store ->
        store.install(bundle(taxonomyId))
        val old = bookmark()
        store.saveBookmark(old)
        store.install(bundle(januaryId))
        val scores = store.games(januaryId)
        assertEquals(857, scores.size)
        assertEquals(39, scores.count { "Site" !in it.tags && "Date" !in it.tags && "Round" !in it.tags })
        // Allow only canonical unknown placeholders, never mismatched/invented supplied metadata.
        val base = bundle(januaryId)
        val target = scores.first { "Site" !in it.tags }
        val changed = scores.map { if (it.id == target.id) it.copy(tags = it.tags + ("Site" to "Invented")) else it }
        val alteredFiles = base.files + ("games.jsonl" to changed.joinToString("") { json.encodeToString(it) + "\n" }.encodeToByteArray())
        val tampered = revised(base.copy(files = alteredFiles), "january-metadata-tamper-fixture") { manifest -> manifest.copy(
            files = manifest.files.map { file -> file.copy(bytes = alteredFiles.getValue(file.name).size.toLong(),
                sha256 = contentSha256(alteredFiles.getValue(file.name))) }) }
        assertFailsWith<IllegalArgumentException> { store.install(tampered) }
        assertEquals(januaryId, store.activePacks().single { it.manifest.source.id == "lichess-broadcast-2020-01" }.manifest.packId)
        assertEquals(old, store.latestBookmark())
        assertEquals(95, store.activePacks().single { it.manifest.packId == januaryId }.manifest.coverage.quarantined)
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val offline = RoomLearningStore(reopened)
            assertEquals(857, offline.games(januaryId).size)
            assertEquals(old, offline.latestBookmark())
            assertEquals(2, offline.activePacks().size)
        } finally { reopened.close() }
    }

    @Test fun installUpdateRollbackRetainsProgressAndOldContent() = database { _, _, store ->
        val base = small()
        store.install(base); store.saveBookmark(bookmark())
        val original = store.openings().single()
        store.install(base) // idempotent
        store.install(revised(base, "fixture-v2"))
        assertEquals("fixture-v2", store.activePacks().single().manifest.packId)
        assertEquals(original, store.openings("fixture-v1").single())
        store.activatePreviousVersion("lichess-openings", "fixture-v1")
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
        assertEquals(bookmark(), store.latestBookmark())
        assertTrue(store.activePacks().single().notices.contains("Lichess"))
        assertEquals(listOf(original.id), store.openingIdsAt(original.positionKey))
        assertFailsWith<IllegalArgumentException> { store.activatePreviousVersion("wrong-source", "fixture-v2") }
    }

    @Test fun brokenChecksumOrMissingFileNeverReplacesActiveContent() = database { _, _, store ->
        val base = small(); store.install(base)
        val broken = revised(base, "fixture-v2").let { it.copy(files = it.files + ("openings.jsonl" to "corrupt".encodeToByteArray())) }
        assertFailsWith<IllegalArgumentException> { store.install(broken) }
        assertEquals("ERROR", store.availability.first().single().state)
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
        assertFailsWith<IllegalArgumentException> { store.install(base.copy(files = base.files - "games.jsonl")) }
        assertFailsWith<IllegalArgumentException> { store.install(base.copy(expectedManifestSha256 = "f".repeat(64))) }
        assertEquals(1, store.openings().size)
    }

    @Test fun transactionFailureAndCancellationRollBackAllRows() = database { _, db, store ->
        val base = small(); store.install(base)
        for (cancel in listOf(false, true)) {
            val failing = RoomLearningStore(db, installCheckpoint = { stage ->
                if (stage == "before-activation") {
                    if (cancel) throw CancellationException("test interruption") else error("test failure after rows")
                }
            })
            assertFails { failing.install(revised(base, "fixture-v2")) }
            assertNull(db.learningDao().packById("fixture-v2"))
            assertTrue(store.openings("fixture-v2").isEmpty())
            assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
            assertEquals("ERROR", store.availability.first().single().state)
        }
    }

    @Test fun abandonedLoadingIsRecoveredOnRelaunch() = database { _, db, store ->
        store.install(small())
        db.learningDao().job(InstallJobEntity("lichess-openings", "fixture-v2", "LOADING", null))
        store.recoverInterruptedInstalls()
        val state = store.availability.first().single()
        assertEquals("ERROR", state.state); assertEquals("fixture-v1", state.activePackId)
        assertTrue(state.error!!.contains("interrupted"))
    }

    @Test fun unsupportedSchemaAndImmutableIdConflictAreRejected() = database { _, _, store ->
        val base = small(); store.install(base)
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "fixture-v3") { it.copy(schemaVersion = 99) }) }
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "fixture-v1") { it.copy(retrievedAt = "changed") }) }
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
    }

    @Test fun checksummedButInconsistentLegalDataAndCountsAreRejected() = database { _, _, store ->
        val base = small(); store.install(base)
        val record = json.decodeFromString<OpeningRecord>(base.files.getValue("openings.jsonl").decodeToString().trim())
        val bytes = (json.encodeToString(record.copy(uci = listOf("e2e5") + record.uci.drop(1))) + "\n").encodeToByteArray()
        val changed = revised(base.copy(files = base.files + ("openings.jsonl" to bytes)), "bad-replay-fixture") { m ->
            m.copy(files = m.files.map { if (it.name == "openings.jsonl") it.copy(bytes = bytes.size.toLong(), sha256 = contentSha256(bytes)) else it })
        }
        assertFailsWith<IllegalArgumentException> { store.install(changed) }
        assertFailsWith<IllegalArgumentException> { store.install(revised(base, "bad-count-fixture") { it.copy(coverage = it.coverage.copy(distinctNames = 99)) }) }
        assertEquals("fixture-v1", store.activePacks().single().manifest.packId)
    }

    @Test fun futureDatabaseVersionFailsWithoutDeletingLearnerData() = database { path, db, store ->
        store.saveBookmark(bookmark()); db.close()
        BundledSQLiteDriver().open(path.toString()).use { it.execSQL("PRAGMA user_version = 99") }
        val future = createJvmLearningDatabase(path.toString())
        try { assertFailsWith<IllegalStateException> { RoomLearningStore(future).latestBookmark() } } finally { future.close() }
        BundledSQLiteDriver().open(path.toString()).use { connection ->
            connection.prepare("SELECT payload FROM bookmarks").use { statement ->
                assertTrue(statement.step()); assertEquals(bookmark(), json.decodeFromString<LessonBookmark>(statement.getText(0)))
            }
        }
    }

    @Test fun pinnedRealPacksReopenOfflineWithExactGameDependency() = database { path, db, store ->
        val games = bundle(broadcastId)
        assertFailsWith<IllegalArgumentException> { store.install(games) }
        store.install(bundle(taxonomyId)); store.install(games)
        assertEquals(3815, store.openings().size)
        assertEquals(setOf("CC0-1.0", "CC-BY-SA-4.0"), store.activePacks().map { it.manifest.source.license }.toSet())
        val expected = json.decodeFromString<GameRecord>(games.files.getValue("games.jsonl").decodeToString().lineSequence().first())
        store.saveBookmark(bookmark())
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val offline = RoomLearningStore(reopened)
            assertEquals(expected, offline.game(expected.id))
            val allScores = offline.games()
            assertEquals(79, allScores.size)
            assertEquals(allScores, offline.games(broadcastId))
            assertTrue(offline.games("missing-fixture").isEmpty())
            val samplePack = offline.activePacks().single { it.manifest.packId == broadcastId }
            val observations = ObservedReplyIndex.build(listOf(ObservedGameSample(samplePack, allScores)))
            assertEquals(79, observations.totalScores)
            val root = observations.at(BoardPosition.starting().positionKey)
            assertEquals(79, root.scoresSeen); assertEquals(79, root.scoresWithReply)
            assertEquals(79, root.replies.sumOf { it.scores })
            assertEquals(samplePack, observations.sources.single())
            val graph = offline.game(expected.id)!!.toLessonGraph()
            for (side in PieceColor.entries) assertEquals(expected.finalFen, graph.start(side).last().position.toFen())
            assertEquals(bookmark(), offline.latestBookmark())
            // Retained dependency remains readable even when a newer taxonomy is active.
            offline.install(revised(bundle(taxonomyId), "taxonomy-updated-fixture"))
            assertEquals(3815, offline.openings(taxonomyId).size)
            offline.install(games)
            assertEquals(allScores, offline.games(broadcastId))
        } finally { reopened.close() }
    }

    @Test fun policyRevisionsAndOldPracticeBookmarkSurviveColdReopen() = database { path, db, store ->
        val graph = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))
        val book = RepertoireBook(graph)
        val original = book.seed(PieceColor.WHITE, "ruy-main")
        val black = book.seed(PieceColor.BLACK, "ruy-main")
        store.saveRepertoirePolicy(original); store.saveRepertoirePolicy(black)
        val practice = book.practiceGraph(original)
        val saved = LessonBookmark(graph.id, "a".repeat(64), practice.start(original.side).jump(5).snapshot(), "STUDY",
            repertoireId = original.id, repertoireRevision = original.revision)
        store.saveBookmark(saved)
        val expanded = book.adoptRoute(original, "ruy-berlin")
        store.saveRepertoirePolicy(expanded)
        store.saveRepertoirePolicy(expanded.copy(preferredMoves = expanded.preferredMoves.entries.reversed().associate { it.key to it.value }))
        store.saveRepertoirePolicy(original) // Idempotent old retry cannot roll the current version back.
        assertEquals(expanded, store.repertoirePolicy(original.id))
        assertFailsWith<IllegalArgumentException> { store.saveRepertoirePolicy(expanded.copy(name = "Concurrent overwrite")) }
        assertFailsWith<IllegalArgumentException> { store.saveRepertoirePolicy(expanded.copy(revision = 3)) }
        assertFailsWith<IllegalArgumentException> { store.saveBookmark(saved.copy(repertoireRevision = 99)) }
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val next = RoomLearningStore(reopened)
            assertEquals(setOf(expanded, black), next.repertoirePolicies.first().toSet())
            assertEquals(original, next.repertoirePolicy(original.id, 0))
            assertEquals(saved, next.latestBookmark())
            assertTrue(next.repertoires().isEmpty()) // Legacy route selection is not silently turned into a policy.
            assertEquals(practice.paths.keys, book.practiceGraph(requireNotNull(next.repertoirePolicy(original.id, 0))).paths.keys)
        } finally { reopened.close() }
    }

    @Test fun policyChoicesSurvivePackReplacementAndUnavailableContent() = database { _, _, store ->
        val book = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")))
        val policy = book.seed(PieceColor.BLACK, "ruy-main")
        store.saveRepertoirePolicy(policy)
        store.install(small()); store.install(revised(small(), "fixture-v2"))
        assertEquals(policy, store.repertoirePolicy(policy.id))
        assertFailsWith<IllegalArgumentException> { book.validate(policy.copy(contentVersion = "f".repeat(64))) }
        assertEquals(policy, store.repertoirePolicy(policy.id))
    }

    @Test fun concurrentPolicyWritersCannotLoseARevision() = database { _, db, store ->
        val book = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")))
        val original = book.seed(PieceColor.WHITE, "ruy-main")
        store.saveRepertoirePolicy(original)
        val candidates = listOf(original.copy(revision = 1, name = "Choice A"), original.copy(revision = 1, name = "Choice B"))
        val outcomes = coroutineScope {
            candidates.map { candidate -> async(Dispatchers.Default) { runCatching { RoomLearningStore(db).saveRepertoirePolicy(candidate) } } }.awaitAll()
        }
        assertEquals(1, outcomes.count { it.isSuccess })
        assertEquals(1, outcomes.count { it.isFailure })
        assertTrue(store.repertoirePolicy(original.id) in candidates)
        assertEquals(original, store.repertoirePolicy(original.id, 0))
    }

    @Test fun migrationOneThroughFiveToSixPreserveAllLearnerRows() = runBlocking {
      for (version in 1..5) {
        val dir = Files.createTempDirectory("opening-lab-migration-test-")
        val path = dir.resolve("learning.db")
        try {
            val schema = json.parseToJsonElement(Files.readString(Path.of("shared/schemas/com.openinglab.shared.storage.LearningDatabase/$version.json")))
                .jsonObject.getValue("database").jsonObject
            BundledSQLiteDriver().open(path.toString()).use { connection ->
                for (entity in schema.getValue("entities").jsonArray) {
                    val e = entity.jsonObject; val name = e.getValue("tableName").jsonPrimitive.content
                    connection.execSQL(e.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
                    e["indices"]?.jsonArray?.forEach { connection.execSQL(it.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name)) }
                }
                schema.getValue("setupQueries").jsonArray.forEach { connection.execSQL(it.jsonPrimitive.content) }
                connection.prepare("INSERT INTO bookmarks VALUES (?, ?, ?)").use { s ->
                    s.bindText(1, bookmark().lessonId); s.bindText(2, json.encodeToString(bookmark())); s.bindLong(3, 123); s.step()
                }
                val attempt = LearningAttempt("migration-attempt", bookmark().lessonId, bookmark().replay.rootPathId, 3, "BLACK", "a7a6", "EXPECTED", false, 456)
                connection.prepare("INSERT INTO attempts VALUES (?, ?, ?, ?)").use { s ->
                    s.bindText(1, attempt.id); s.bindText(2, attempt.lessonId); s.bindText(3, json.encodeToString(attempt)); s.bindLong(4, 456); s.step()
                }
                val selection = RepertoireSelection(bookmark().lessonId, "BLACK", bookmark().replay.rootPathId)
                connection.prepare("INSERT INTO repertoires VALUES (?, ?)").use { s ->
                    s.bindText(1, selection.lessonId); s.bindText(2, json.encodeToString(selection)); s.step()
                }
                val pack = small()
                val manifest = json.decodeFromString<ContentManifest>(pack.manifest.decodeToString())
                connection.prepare(if (version == 1) "INSERT INTO packs VALUES (?, ?, ?, ?)" else "INSERT INTO packs VALUES (?, ?, ?, ?, ?)").use { s ->
                    s.bindText(1, manifest.packId); s.bindText(2, manifest.source.id); s.bindText(3, pack.manifest.decodeToString()); s.bindText(4, pack.expectedManifestSha256)
                    if (version >= 2) s.bindText(5, "Retained source notices"); s.step()
                }
                connection.prepare("INSERT INTO active_packs VALUES (?, ?)").use { s -> s.bindText(1, manifest.source.id); s.bindText(2, manifest.packId); s.step() }
                if (version >= 3) {
                    val policy = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))).seed(PieceColor.WHITE, "ruy-main")
                    connection.prepare("INSERT INTO repertoire_policy_versions VALUES (?, ?, ?, ?, ?)").use { s ->
                        s.bindText(1, policy.id); s.bindLong(2, 0); s.bindText(3, policy.lessonId); s.bindText(4, json.encodeToString(policy)); s.bindLong(5, 789); s.step()
                    }
                    connection.prepare("INSERT INTO active_repertoire_policies VALUES (?, ?)").use { s -> s.bindText(1, policy.id); s.bindLong(2, 0); s.step() }
                    if (version >= 4) {
                        val set = RepertoireSet("retained-set", "Retained set", PieceColor.WHITE, listOf(RepertoirePolicyRef(policy.id, 0)))
                        connection.prepare("INSERT INTO repertoire_set_versions VALUES (?, ?, ?, ?)").use { s ->
                            s.bindText(1, set.id); s.bindLong(2, 0); s.bindText(3, json.encodeToString(set)); s.bindLong(4, 890); s.step()
                        }
                        connection.prepare("INSERT INTO active_repertoire_sets VALUES (?, ?)").use { s -> s.bindText(1, set.id); s.bindLong(2, 0); s.step() }
                    }
                }
                if (version == 5) {
                    val followed = FollowedPlayer("synthetic-migration-player", listOf("Synthetic retained player"), "SOURCE_SCOPED_UNVERIFIED")
                    connection.prepare("INSERT INTO followed_players VALUES (?, ?)").use { s -> s.bindText(1, followed.id); s.bindText(2, json.encodeToString(followed)); s.step() }
                    val private = PrivateGameRecord.import("[White \"Synthetic retained player\"]\n\n1. e4 e5 *")
                    connection.prepare("INSERT INTO private_games VALUES (?, ?, ?)").use { s -> s.bindText(1, private.id); s.bindText(2, json.encodeToString(private)); s.bindLong(3, 1000); s.step() }
                }
                connection.execSQL("PRAGMA user_version = $version")
            }
            val db = createJvmLearningDatabase(path.toString())
            try {
                val store = RoomLearningStore(db)
                assertEquals(bookmark(), store.latestBookmark())
                assertEquals(1, store.attempts(bookmark().lessonId).size)
                assertEquals(RepertoireSelection(bookmark().lessonId, "BLACK", bookmark().replay.rootPathId), store.repertoires().single())
                assertEquals(if (version == 1) "" else "Retained source notices", store.activePacks().single().notices)
                val policy = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))).seed(PieceColor.WHITE, "ruy-main")
                assertEquals(if (version >= 3) listOf(policy) else emptyList(), store.repertoirePolicies.first())
                assertEquals(if (version >= 4) listOf(RepertoireSet("retained-set", "Retained set", PieceColor.WHITE, listOf(RepertoirePolicyRef(policy.id, 0)))) else emptyList(), store.repertoireSets.first())
                if (version == 5) {
                    assertEquals("synthetic-migration-player", store.followedPlayers.first().single().id)
                    assertEquals("Synthetic retained player", store.privateGames.first().single().white.name)
                } else { assertTrue(store.followedPlayers.first().isEmpty()); assertTrue(store.privateGames.first().isEmpty()) }
                assertEquals(1, store.learningTotals.first().legacyUngraded)
                assertTrue(store.recallScopes(1000).first().isEmpty())
                store.saveRepertoirePolicy(policy); assertEquals(policy, store.repertoirePolicies.first().single())
            }
            finally { db.close() }
        } finally { Files.walk(dir).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
      }
    }

    private fun recallBook() = RepertoireBook(LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez")))
    private fun recallEvent(enrollment: RecallEnrollment, id: String, at: Long, grade: RecallGrade = RecallGrade.UNAIDED): RecallEvent {
        val t = enrollment.entries.first().target
        return RecallEvent(LearningAttempt(id, t.lessonId, t.pathId, t.ply, t.side.name,
            if (grade == RecallGrade.NOT_RECALLED) "d2d4" else t.expectedUci,
            if (grade == RecallGrade.NOT_RECALLED) "LEGAL_OFF_LINE" else "EXPECTED", grade != RecallGrade.UNAIDED, at),
            t, grade, if (grade == RecallGrade.ASSISTED) setOf(RecallHelp.ENGINE) else emptySet(), enrollment.scope.id)
    }
    @Test fun recallScopesAtomicAttemptsAndStudyCountsColdReopenWithoutFakeLegacyMastery() = database { path, db, store ->
        val enrollment = RecallPlanner.route(recallBook(), PieceColor.WHITE, "ruy-main")
        store.recordAttempt(LearningAttempt("legacy", "ruy-lopez", "ruy-main", 0, "WHITE", "e2e4", "EXPECTED", false, 123))
        store.enrollRecall(enrollment)
        assertEquals(enrollment.entries.size, store.recallScopes(1_000_000).first().single().due)
        val event = recallEvent(enrollment, "graded", 1_000_000)
        store.recordRecall(event); store.recordRecall(event)
        store.recordStudyView("study", enrollment.scope.id, 1_000_000); store.recordStudyView("study", enrollment.scope.id, 1_000_000)
        assertEquals(LearningTotals(2, 1, 0, 0, 1, 1), store.learningTotals.first())
        assertEquals(enrollment.entries.size - 1, store.recallCards(enrollment.scope.id, 1_000_000).size)
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val cold = RoomLearningStore(reopened)
            assertEquals(1, cold.learningTotals.first().unaided)
            val due = cold.recallCards(enrollment.scope.id, 1_000_000 + RecallScheduler.DAY)
            assertEquals(1, due.single { it.target.id == event.target.id }.state.spacedSuccesses)
            assertEquals(0, cold.recallScopes(1_000_000).first().single().established)
        } finally { reopened.close() }
    }
    @Test fun assistanceFailureAndOutOfOrderAttemptsAreRetainedButNeverPromotedToUnaidedSpacing() = database { _, _, store ->
        val e = RecallPlanner.route(recallBook(), PieceColor.WHITE, "ruy-main")
        store.enrollRecall(e)
        store.recordRecall(recallEvent(e, "engine", 1_000_000, RecallGrade.ASSISTED))
        store.recordRecall(recallEvent(e, "early", 1_000_001))
        var card = store.recallCards(e.scope.id, 2_000_000).single { it.target.id == e.entries.first().target.id }
        assertEquals(0, card.state.spacedSuccesses)
        store.recordRecall(recallEvent(e, "due", 2_000_000))
        store.recordRecall(recallEvent(e, "late-write", 900_000, RecallGrade.NOT_RECALLED))
        card = store.recallCards(e.scope.id, 2_000_000 + RecallScheduler.DAY).single { it.target.id == card.target.id }
        assertEquals(1, card.state.spacedSuccesses)
        assertEquals(2_000_000, card.state.lastAt)
        assertEquals(LearningTotals(4, 2, 1, 1, 0, 0), store.learningTotals.first())
    }
    @Test fun expandedChosenPolicyUsesActualDenominatorAndRetainsPreviousRevisionCards() = database { _, _, store ->
        val book = recallBook(); val policy = book.seed(PieceColor.WHITE, "ruy-main")
        store.saveRepertoirePolicy(policy)
        val first = RecallPlanner.policy(book, policy); store.enrollRecall(first)
        store.recordRecall(recallEvent(first, "first-policy", 1_000_000))
        val changed = book.adoptRoute(policy, "ruy-berlin"); store.saveRepertoirePolicy(changed)
        val expanded = RecallPlanner.policy(book, changed); store.enrollRecall(expanded)
        val summary = store.recallScopes(1_000_000).first().single()
        assertEquals(expanded.entries.size, summary.total)
        assertEquals(1, summary.introduced)
        assertEquals(expanded.scope, summary.scope)
        assertNotNull(store.recallCards(first.scope.id, 1_000_000 + RecallScheduler.DAY).singleOrNull { it.target.id == first.entries.first().target.id })
    }
    @Test fun invalidScopeEventCollisionAndMissingPolicyRollbackWithoutLosingRows() = database { _, _, store ->
        val book = recallBook(); val route = RecallPlanner.route(book, PieceColor.WHITE, "ruy-main")
        assertFails { store.enrollRecall(RecallPlanner.policy(book, book.seed(PieceColor.WHITE, "ruy-main"))) }
        assertTrue(store.recallScopes(1_000_000).first().isEmpty())
        store.enrollRecall(route)
        val event = recallEvent(route, "idempotent", 1_000_000); store.recordRecall(event)
        assertFails { store.recordRecall(event.copy(attempt = event.attempt.copy(recordedAt = 2_000_000))) }
        assertFails { store.recordRecall(event.copy(scopeId = "f".repeat(64), attempt = event.attempt.copy(id = "missing"))) }
        assertEquals(1, store.learningTotals.first().attempts)
        assertEquals(route.entries.size, store.recallScopes(1_000_000).first().single().total)
    }
    @Test fun attemptInsertionFailureRollsBackGradeAndChangedContentRetainsExactOldCards() = database { _, db, store ->
        val old = RecallPlanner.route(recallBook(), PieceColor.WHITE, "ruy-main"); store.enrollRecall(old)
        val event = recallEvent(old, "legacy-collision", 1_000_000)
        store.recordAttempt(event.attempt)
        assertFails { store.recordRecall(event) }
        assertFalse(store.hasRecallEvent(event.attempt.id, event.target.id))
        assertEquals(0, store.recallCard(old.scope.id, event.target.id)!!.state.spacedSuccesses)
        store.recordRecall(event.copy(attempt = event.attempt.copy(id = "new-success")))
        val opening = OpeningCatalog.byId("ruy-lopez")
        val changedBook = RepertoireBook(LessonGraph.fromOpening(opening.copy(variations = opening.variations.filter { it.id == "ruy-main" })))
        val changed = RecallPlanner.route(changedBook, PieceColor.WHITE, "ruy-main"); store.enrollRecall(changed)
        assertEquals(0, store.recallScopes(1_000_000).first().single().introduced)
        assertEquals(1, store.recallCard(old.scope.id, event.target.id)!!.state.spacedSuccesses)
        assertEquals(old.entries.size + changed.entries.size, db.learningDao().recallCardCount())
        assertEquals(2, store.learningTotals.first().attempts)
    }
    @Test fun establishedDenominatorRequiresDueSeparatedAnswersAndOverdueStatusIsExplicit() = database { _, _, store ->
        val e = RecallPlanner.route(recallBook(), PieceColor.WHITE, "ruy-main"); store.enrollRecall(e)
        var at = 1_000_000L
        repeat(3) { index ->
            store.recordRecall(recallEvent(e, "spaced-$index", at))
            at = store.recallCard(e.scope.id, e.entries.first().target.id)!!.state.dueAt
        }
        assertEquals(1, store.recallScopes(at - 1).first().single().established)
        assertEquals(0, store.recallScopes(at).first().single().established)
        store.recordStudyView("separate-study", e.scope.id, at)
        assertEquals(3, store.learningTotals.first().unaided)
        assertEquals(3, store.recallCard(e.scope.id, e.entries.first().target.id)!!.state.spacedSuccesses)
    }

    @Test fun privateImportsAndExplicitFollowsColdRestoreWithoutChangingPublicPacksOrBookmarks() = database { path, db, store ->
        val old = bookmark(); store.saveBookmark(old)
        assertTrue(store.followedPlayers.first().isEmpty())
        val text = "[White \"Synthetic Alpha\"]\n[Black \"Synthetic Beta\"]\n\n1. e4 e5 2. Nf3 Nc6 *"
        val imported = store.importPrivateGame(text)
        imported.checkedGame()
        assertEquals(imported, store.importPrivateGame(imported.canonicalPgn))
        assertEquals(1, store.privateGames.first().size)
        assertTrue(store.games().isEmpty()); assertTrue(store.activePacks().isEmpty())
        val follow = FollowedPlayer(imported.white.id, listOf(imported.white.name), imported.white.identityStatus)
        store.followPlayer(follow)
        assertEquals(listOf(follow), store.followedPlayers.first())
        assertFailsWith<IllegalArgumentException> { store.importPrivateGame("1. e5 *") }
        assertEquals(old, store.latestBookmark())
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val restored = RoomLearningStore(reopened)
            assertEquals(imported, restored.privateGame(imported.id))
            assertEquals(listOf(imported), restored.privateGames.first())
            assertEquals(listOf(follow), restored.followedPlayers.first())
            restored.unfollowPlayer(follow.id)
            assertTrue(restored.followedPlayers.first().isEmpty())
            assertEquals(imported, restored.privateGame(imported.id)) // Unfollowing never deletes games.
            assertEquals(old, restored.latestBookmark())
        } finally { reopened.close() }
    }

    @Test fun simultaneousPrivateImportWritersAreIdempotent() = database { _, db, store ->
        val text = "[White \"Synthetic\"]\n[Black \"Fixture\"]\n\n1. d4 d5 *"
        val records = coroutineScope { (1..2).map { async(Dispatchers.Default) { RoomLearningStore(db).importPrivateGame(text) } }.awaitAll() }
        assertEquals(records[0], records[1])
        assertEquals(listOf(records.first()), store.privateGames.first())
    }

    @Test fun gameBookmarksPinRetainedPublicVersionAndPrivateIdentityAcrossColdReopen() = database { path, db, store ->
        store.install(bundle(taxonomyId)); store.install(bundle(broadcastId))
        val pack = store.retainedPack(broadcastId)!!
        val score = store.games(broadcastId).first()
        val ref = GameStudyReference(score.id, broadcastId, pack.manifestSha256)
        val graph = score.toLessonGraph()
        val saved = LessonBookmark(ref.lessonId, contentSha256(score.canonicalPgn.encodeToByteArray()), graph.start(PieceColor.BLACK).jump(10).snapshot(), "STUDY", gameReference = ref)
        store.saveBookmark(saved)
        store.install(revised(bundle(broadcastId), "broadcast-new-version-fixture"))
        assertNotEquals(broadcastId, store.activePacks().single { it.manifest.source.id == pack.manifest.source.id }.manifest.packId)
        assertEquals(pack, store.retainedPack(broadcastId)); assertEquals(score, store.gameInPack(broadcastId, score.id))
        assertFailsWith<IllegalArgumentException> { store.saveBookmark(saved.copy(gameReference = ref.copy(manifestSha256 = "b".repeat(64)))) }
        assertFailsWith<IllegalArgumentException> { store.saveBookmark(saved.copy(mode = "PRACTICE")) }
        assertEquals(saved, store.latestBookmark())
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val cold = RoomLearningStore(reopened)
            assertEquals(saved, cold.latestBookmark()); assertEquals(pack, cold.retainedPack(broadcastId))
            assertEquals(score, cold.gameInPack(broadcastId, score.id))
            val private = cold.importPrivateGame("1. d4 d5 *")
            val privateRef = GameStudyReference(private.id, privateImport = true)
            val privateGraph = LessonGraph.fromPgn(private.checkedGame(), privateRef.lessonId)
            val privateBook = LessonBookmark(privateRef.lessonId, contentSha256(private.canonicalPgn.encodeToByteArray()), privateGraph.start(PieceColor.WHITE).jump(1).snapshot(), "STUDY", gameReference = privateRef)
            cold.saveBookmark(privateBook); assertEquals(privateBook, cold.latestBookmark())
            assertTrue(cold.repertoires().isEmpty()); assertTrue(cold.attempts(privateRef.lessonId).isEmpty())
        } finally { reopened.close() }
    }

    @Test fun setVersionsPinPoliciesRejectStaleWritesAndSurviveColdReopen() = database { path, db, store ->
        val graph = LessonGraph.fromOpening(OpeningCatalog.byId("ruy-lopez"))
        val policy = RepertoireBook(graph).seed(PieceColor.WHITE, "ruy-main")
        store.saveRepertoirePolicy(policy)
        val set = RepertoireSet("test-white-set", "White e4", PieceColor.WHITE, listOf(RepertoirePolicyRef(policy.id, 0)))
        store.saveRepertoireSet(set)
        store.saveRepertoirePolicy(policy.copy(revision = 1))
        assertEquals(set, store.repertoireSet(set.id)) // Not rebound to policy revision1.
        val newer = set.copy(revision = 1, name = "Updated white e4", members = listOf(RepertoirePolicyRef(policy.id, 1)))
        store.saveRepertoireSet(newer); store.saveRepertoireSet(set)
        assertEquals(newer, store.repertoireSets.first().single())
        assertFailsWith<IllegalArgumentException> { store.saveRepertoireSet(newer.copy(name = "Overwrite")) }
        assertFailsWith<IllegalArgumentException> { store.saveRepertoireSet(newer.copy(revision = 3)) }
        assertFailsWith<IllegalArgumentException> { store.saveRepertoireSet(set.copy(id = "bad-side", side = PieceColor.BLACK)) }
        assertFailsWith<IllegalArgumentException> { store.saveRepertoireSet(set.copy(id = "missing", members = listOf(RepertoirePolicyRef(policy.id, 99)))) }
        val saved = LessonBookmark(graph.id, "a".repeat(64), graph.start(PieceColor.WHITE).jump(3).snapshot(), "STUDY",
            repertoireId = policy.id, repertoireRevision = 0, repertoireSetId = set.id, repertoireSetRevision = 0, repertoireSetIndex = 0)
        store.saveBookmark(saved)
        db.close()
        val reopened = createJvmLearningDatabase(path.toString())
        try {
            val cold = RoomLearningStore(reopened)
            assertEquals(newer, cold.repertoireSets.first().single()); assertEquals(set, cold.repertoireSet(set.id, 0))
            assertEquals(policy, cold.repertoirePolicy(policy.id, 0)); assertEquals(saved, cold.latestBookmark())
        } finally { reopened.close() }
    }
}

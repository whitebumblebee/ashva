package com.openinglab.shared.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "daily_activity")
data class StudyActivityEntity(@PrimaryKey val id: String, val lessonId: String, val pathId: String,
    val kind: String, val recordedAt: Long)

@Entity(tableName = "packs", indices = [Index("sourceId")])
data class PackEntity(@PrimaryKey val packId: String, val sourceId: String, val manifest: String, val manifestHash: String,
    @ColumnInfo(defaultValue = "''") val notices: String = "")
@Entity(tableName = "active_packs")
data class ActivePackEntity(@PrimaryKey val sourceId: String, val packId: String)
@Entity(tableName = "install_jobs")
data class InstallJobEntity(@PrimaryKey val sourceId: String, val requestedPackId: String, val state: String, val error: String?)
@Entity(tableName = "content_records", primaryKeys = ["packId", "recordId"], indices = [Index("recordId"), Index("kind"), Index("positionKey")])
data class ContentRecordEntity(val packId: String, val recordId: String, val kind: String, val title: String, val positionKey: String, val payload: String)
@Entity(tableName = "positions", primaryKeys = ["packId", "recordId", "ply"], indices = [Index("positionKey")])
data class PositionEntity(val packId: String, val recordId: String, val ply: Int, val positionKey: String)
// No foreign keys into replaceable content: learner state must survive a missing/changed pack.
@Entity(tableName = "bookmarks")
data class BookmarkEntity(@PrimaryKey val lessonId: String, val payload: String, val updatedAt: Long)
@Entity(tableName = "attempts", indices = [Index("lessonId")])
data class AttemptEntity(@PrimaryKey val id: String, val lessonId: String, val payload: String, val recordedAt: Long)
@Entity(tableName = "repertoires")
data class RepertoireEntity(@PrimaryKey val lessonId: String, val payload: String)
@Entity(tableName = "annotations", primaryKeys = ["packId", "annotationId"])
data class AnnotationEntity(val packId: String, val annotationId: String, val recordId: String, val status: String, val sourceId: String, val payload: String)
@Entity(tableName = "repertoire_policy_versions", primaryKeys = ["id", "revision"], indices = [Index("lessonId")])
data class RepertoirePolicyEntity(val id: String, val revision: Int, val lessonId: String, val payload: String, val updatedAt: Long)
@Entity(tableName = "active_repertoire_policies")
data class ActiveRepertoirePolicyEntity(@PrimaryKey val id: String, val revision: Int)
@Entity(tableName = "repertoire_set_versions", primaryKeys = ["id", "revision"])
data class RepertoireSetEntity(val id: String, val revision: Int, val payload: String, val updatedAt: Long)
@Entity(tableName = "active_repertoire_sets")
data class ActiveRepertoireSetEntity(@PrimaryKey val id: String, val revision: Int)

@Entity(tableName = "followed_players")
data class FollowedPlayerEntity(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "private_games")
data class PrivateGameEntity(@PrimaryKey val id: String, val payload: String, val importedAt: Long)

@Entity(tableName = "recall_cards", indices = [Index("dueAt")])
data class RecallCardEntity(@PrimaryKey val id: String, val target: String, val state: String,
    val dueAt: Long, val lastAt: Long?, val spacedSuccesses: Int)
@Entity(tableName = "recall_scopes")
data class RecallScopeEntity(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "active_recall_scopes")
data class ActiveRecallScopeEntity(@PrimaryKey val groupId: String, val scopeId: String)
@Entity(tableName = "recall_scope_cards", primaryKeys = ["scopeId", "cardId"], indices = [Index("cardId")])
data class RecallScopeCardEntity(val scopeId: String, val cardId: String, val context: String)
@Entity(tableName = "recall_events", indices = [Index("cardId")])
data class RecallEventEntity(@PrimaryKey val id: String, val cardId: String, val grade: String, val recordedAt: Long, val payload: String)
@Entity(tableName = "study_views")
data class StudyViewEntity(@PrimaryKey val id: String, val scopeId: String, val recordedAt: Long)
data class RecallSummaryRow(val payload: String, val total: Int, val introduced: Int, val established: Int, val due: Int, val nextDueAt: Long?)
data class RecallCardRow(val target: String, val state: String, val context: String)
data class TotalsRow(val attempts: Int, val unaided: Int, val assisted: Int, val notRecalled: Int, val legacyUngraded: Int, val studyViews: Int)
data class StudyRouteRow(val id: String, val recordedAt: Long, val scopePayload: String, val target: String, val context: String)

data class AvailabilityRow(val sourceId: String, val requestedPackId: String, val state: String, val error: String?, val activePackId: String?)

@Dao
interface LearningDao {
    @Query("SELECT recordedAt FROM attempts UNION ALL SELECT recordedAt FROM recall_events UNION ALL SELECT recordedAt FROM study_views UNION ALL SELECT recordedAt FROM daily_activity UNION ALL SELECT at AS recordedAt FROM tactics_attempts")
    fun activityTimes(): Flow<List<Long>>
    @Query("SELECT * FROM daily_activity ORDER BY recordedAt, id")
    fun openingActivity(): Flow<List<StudyActivityEntity>>
    @Query("SELECT v.id, v.recordedAt, s.payload AS scopePayload, c.target, m.context FROM study_views v JOIN recall_scopes s ON s.id = v.scopeId JOIN recall_scope_cards m ON m.scopeId = v.scopeId AND m.cardId = (SELECT MIN(cardId) FROM recall_scope_cards WHERE scopeId = v.scopeId) JOIN recall_cards c ON c.id = m.cardId ORDER BY v.recordedAt, v.id")
    fun studyRoutes(): Flow<List<StudyRouteRow>>
    @Query("SELECT COUNT(DISTINCT c.id) FROM recall_cards c JOIN recall_scope_cards m ON m.cardId = c.id JOIN active_recall_scopes a ON a.scopeId = m.scopeId WHERE c.dueAt <= :at")
    fun dueCount(at: Long): Flow<Int>
    @Query("SELECT * FROM daily_activity WHERE id = :id")
    suspend fun openingActivityById(id: String): StudyActivityEntity?
    @Insert suspend fun openingActivity(value: StudyActivityEntity)
    @Query("SELECT j.*, a.packId AS activePackId FROM install_jobs j LEFT JOIN active_packs a ON j.sourceId = a.sourceId ORDER BY j.sourceId")
    fun availability(): Flow<List<AvailabilityRow>>
    @Query("UPDATE install_jobs SET state = 'ERROR', error = 'Installation interrupted; previous content remains available. Retry installation.' WHERE state = 'LOADING'")
    suspend fun recoverInterrupted()
    @Upsert suspend fun job(value: InstallJobEntity)
    @Insert suspend fun pack(value: PackEntity)
    @Upsert suspend fun activate(value: ActivePackEntity)
    @Query("SELECT * FROM packs WHERE packId = :id") suspend fun packById(id: String): PackEntity?
    @Query("SELECT p.* FROM packs p JOIN active_packs a ON p.packId = a.packId ORDER BY p.sourceId") suspend fun activePacks(): List<PackEntity>
    @Insert suspend fun records(values: List<ContentRecordEntity>)
    @Insert suspend fun positions(values: List<PositionEntity>)
    @Query("SELECT r.payload FROM content_records r JOIN active_packs a ON r.packId = a.packId WHERE r.kind = :kind ORDER BY r.recordId")
    suspend fun records(kind: String): List<String>
    @Query("SELECT payload FROM content_records WHERE packId = :packId AND kind = :kind ORDER BY recordId")
    suspend fun recordsInPack(packId: String, kind: String): List<String>
    @Query("SELECT r.recordId FROM content_records r JOIN active_packs a ON r.packId = a.packId WHERE r.kind = 'OPENING' AND r.positionKey = :key ORDER BY r.recordId")
    suspend fun openingIdsAt(key: String): List<String>
    @Query("SELECT payload FROM repertoires ORDER BY lessonId") suspend fun repertoires(): List<String>
    @Query("SELECT r.payload FROM content_records r JOIN active_packs a ON r.packId = a.packId WHERE r.kind = 'GAME' AND r.recordId = :id LIMIT 1")
    suspend fun game(id: String): String?
    @Query("SELECT payload FROM content_records WHERE kind = 'GAME' AND packId = :packId AND recordId = :id")
    suspend fun gameInPack(packId: String, id: String): String?
    @Query("SELECT payload FROM followed_players ORDER BY id") fun followedPlayers(): Flow<List<String>>
    @Upsert suspend fun follow(value: FollowedPlayerEntity)
    @Query("DELETE FROM followed_players WHERE id = :id") suspend fun unfollow(id: String)
    @Query("SELECT payload FROM private_games ORDER BY importedAt DESC, id") fun privateGames(): Flow<List<String>>
    @Query("SELECT payload FROM private_games WHERE id = :id") suspend fun privateGame(id: String): String?
    @Query("SELECT COUNT(*) FROM private_games") suspend fun privateGameCount(): Int
    @Query("SELECT COALESCE(SUM(LENGTH(CAST(payload AS BLOB))), 0) FROM private_games") suspend fun privateGameBytes(): Long
    @Insert suspend fun privateGame(value: PrivateGameEntity)
    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC, lessonId LIMIT 1") suspend fun latestBookmark(): BookmarkEntity?
    @Upsert suspend fun bookmark(value: BookmarkEntity)
    @Insert suspend fun attempt(value: AttemptEntity)
    @Query("SELECT payload FROM attempts WHERE lessonId = :lessonId ORDER BY recordedAt, id") suspend fun attempts(lessonId: String): List<String>
    @Upsert suspend fun repertoire(value: RepertoireEntity)
    @Query("SELECT p.* FROM repertoire_policy_versions p JOIN active_repertoire_policies a ON p.id = a.id AND p.revision = a.revision ORDER BY p.updatedAt DESC, p.id")
    fun repertoirePolicies(): Flow<List<RepertoirePolicyEntity>>
    @Query("SELECT p.* FROM repertoire_policy_versions p JOIN active_repertoire_policies a ON p.id = a.id AND p.revision = a.revision WHERE p.id = :id")
    suspend fun currentPolicy(id: String): RepertoirePolicyEntity?
    @Query("SELECT * FROM repertoire_policy_versions WHERE id = :id AND revision = :revision")
    suspend fun policyVersion(id: String, revision: Int): RepertoirePolicyEntity?
    @Insert suspend fun policy(value: RepertoirePolicyEntity)
    @Upsert suspend fun activatePolicy(value: ActiveRepertoirePolicyEntity)
    @Query("SELECT s.* FROM repertoire_set_versions s JOIN active_repertoire_sets a ON s.id = a.id AND s.revision = a.revision ORDER BY s.updatedAt DESC, s.id")
    fun repertoireSets(): Flow<List<RepertoireSetEntity>>
    @Query("SELECT s.* FROM repertoire_set_versions s JOIN active_repertoire_sets a ON s.id = a.id AND s.revision = a.revision WHERE s.id = :id")
    suspend fun currentSet(id: String): RepertoireSetEntity?
    @Query("SELECT * FROM repertoire_set_versions WHERE id = :id AND revision = :revision")
    suspend fun setVersion(id: String, revision: Int): RepertoireSetEntity?
    @Insert suspend fun repertoireSet(value: RepertoireSetEntity)
    @Upsert suspend fun activateSet(value: ActiveRepertoireSetEntity)
    @Query("SELECT s.payload, COUNT(c.id) AS total, COALESCE(SUM(c.lastAt IS NOT NULL),0) AS introduced, COALESCE(SUM(c.spacedSuccesses >= 3 AND c.dueAt > :at),0) AS established, COALESCE(SUM(c.dueAt <= :at),0) AS due, MIN(CASE WHEN c.dueAt > :at THEN c.dueAt END) AS nextDueAt FROM recall_scopes s JOIN active_recall_scopes a ON a.scopeId = s.id JOIN recall_scope_cards m ON m.scopeId = s.id JOIN recall_cards c ON c.id = m.cardId GROUP BY s.id ORDER BY s.id")
    fun recallScopes(at: Long): Flow<List<RecallSummaryRow>>
    @Query("SELECT (SELECT COUNT(*) FROM attempts) AS attempts, (SELECT COUNT(*) FROM recall_events WHERE grade = 'UNAIDED') AS unaided, (SELECT COUNT(*) FROM recall_events WHERE grade = 'ASSISTED') AS assisted, (SELECT COUNT(*) FROM recall_events WHERE grade = 'NOT_RECALLED') AS notRecalled, (SELECT COUNT(*) FROM attempts a WHERE NOT EXISTS (SELECT 1 FROM recall_events e WHERE e.id = a.id)) AS legacyUngraded, (SELECT COUNT(*) FROM study_views) AS studyViews")
    fun learningTotals(): Flow<TotalsRow>
    @Query("SELECT c.target, c.state, m.context FROM recall_cards c JOIN recall_scope_cards m ON m.cardId = c.id WHERE m.scopeId = :scopeId AND c.dueAt <= :at ORDER BY c.dueAt, c.id LIMIT :limit")
    suspend fun recallCards(scopeId: String, at: Long, limit: Int): List<RecallCardRow>
    @Query("SELECT c.target, c.state, m.context FROM recall_cards c JOIN recall_scope_cards m ON m.cardId = c.id WHERE m.scopeId = :scopeId AND c.id = :cardId")
    suspend fun scopedRecallCard(scopeId: String, cardId: String): RecallCardRow?
    @Query("SELECT * FROM recall_cards WHERE id = :id") suspend fun recallCard(id: String): RecallCardEntity?
    @Query("SELECT * FROM recall_events WHERE id = :id") suspend fun recallEvent(id: String): RecallEventEntity?
    @Query("SELECT * FROM recall_scopes WHERE id = :id") suspend fun recallScope(id: String): RecallScopeEntity?
    @Query("SELECT * FROM recall_scope_cards WHERE scopeId = :scopeId AND cardId = :cardId") suspend fun recallMembership(scopeId: String, cardId: String): RecallScopeCardEntity?
    @Query("SELECT COUNT(*) FROM recall_cards") suspend fun recallCardCount(): Int
    @Query("SELECT COUNT(*) FROM recall_scopes") suspend fun recallScopeCount(): Int
    @Query("SELECT * FROM study_views WHERE id = :id") suspend fun studyView(id: String): StudyViewEntity?
    @Insert suspend fun recallScope(value: RecallScopeEntity)
    @Insert suspend fun recallMemberships(values: List<RecallScopeCardEntity>)
    @Upsert suspend fun recallCard(value: RecallCardEntity)
    @Upsert suspend fun activateRecallScope(value: ActiveRecallScopeEntity)
    @Insert suspend fun recallEvent(value: RecallEventEntity)
    @Insert suspend fun studyView(value: StudyViewEntity)
}

@Database(entities = [PackEntity::class, ActivePackEntity::class, InstallJobEntity::class,
    ContentRecordEntity::class, PositionEntity::class, BookmarkEntity::class, AttemptEntity::class,
    RepertoireEntity::class, AnnotationEntity::class, RepertoirePolicyEntity::class,
    ActiveRepertoirePolicyEntity::class, RepertoireSetEntity::class, ActiveRepertoireSetEntity::class,
    FollowedPlayerEntity::class, PrivateGameEntity::class, RecallCardEntity::class, RecallScopeEntity::class,
    ActiveRecallScopeEntity::class, RecallScopeCardEntity::class, RecallEventEntity::class, StudyViewEntity::class,
    TacticsCustomSetEntity::class, TacticsCycleEntity::class, TacticsAttemptEntity::class,
    StudyActivityEntity::class], version = 8, exportSchema = true)
@ConstructedBy(LearningDatabaseConstructor::class)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    abstract fun tacticsDao(): TacticsDao
    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS daily_activity (id TEXT NOT NULL, lessonId TEXT NOT NULL, pathId TEXT NOT NULL, kind TEXT NOT NULL, recordedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS tactics_custom_sets (id TEXT NOT NULL, name TEXT NOT NULL, specJson TEXT NOT NULL, puzzleIdsJson TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS tactics_cycles (setId TEXT NOT NULL, cycle INTEGER NOT NULL, startedAt INTEGER NOT NULL, completedAt INTEGER, activeMs INTEGER NOT NULL, PRIMARY KEY(setId, cycle))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS tactics_attempts (setId TEXT NOT NULL, cycle INTEGER NOT NULL, puzzleId TEXT NOT NULL, ordinal INTEGER NOT NULL, correct INTEGER NOT NULL, activeMs INTEGER NOT NULL, at INTEGER NOT NULL, PRIMARY KEY(setId, cycle, puzzleId))")
            }
        }
        // v1 is a pre-release schema fixture, not a previously shipped production database.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE packs ADD COLUMN notices TEXT NOT NULL DEFAULT ''")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS repertoire_policy_versions (id TEXT NOT NULL, revision INTEGER NOT NULL, lessonId TEXT NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id, revision))")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_repertoire_policy_versions_lessonId ON repertoire_policy_versions (lessonId)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS active_repertoire_policies (id TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS repertoire_set_versions (id TEXT NOT NULL, revision INTEGER NOT NULL, payload TEXT NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id, revision))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS active_repertoire_sets (id TEXT NOT NULL, revision INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS followed_players (id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS private_games (id TEXT NOT NULL, payload TEXT NOT NULL, importedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE TABLE IF NOT EXISTS recall_cards (id TEXT NOT NULL, target TEXT NOT NULL, state TEXT NOT NULL, dueAt INTEGER NOT NULL, lastAt INTEGER, spacedSuccesses INTEGER NOT NULL, PRIMARY KEY(id))")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_recall_cards_dueAt ON recall_cards (dueAt)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS recall_scopes (id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS active_recall_scopes (groupId TEXT NOT NULL, scopeId TEXT NOT NULL, PRIMARY KEY(groupId))")
                connection.execSQL("CREATE TABLE IF NOT EXISTS recall_scope_cards (scopeId TEXT NOT NULL, cardId TEXT NOT NULL, context TEXT NOT NULL, PRIMARY KEY(scopeId, cardId))")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_recall_scope_cards_cardId ON recall_scope_cards (cardId)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS recall_events (id TEXT NOT NULL, cardId TEXT NOT NULL, grade TEXT NOT NULL, recordedAt INTEGER NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(id))")
                connection.execSQL("CREATE INDEX IF NOT EXISTS index_recall_events_cardId ON recall_events (cardId)")
                connection.execSQL("CREATE TABLE IF NOT EXISTS study_views (id TEXT NOT NULL, scopeId TEXT NOT NULL, recordedAt INTEGER NOT NULL, PRIMARY KEY(id))")
            }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LearningDatabaseConstructor : RoomDatabaseConstructor<LearningDatabase> {
    override fun initialize(): LearningDatabase
}

package com.openinglab.shared.storage

import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.Flow

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

data class AvailabilityRow(val sourceId: String, val requestedPackId: String, val state: String, val error: String?, val activePackId: String?)

@Dao
interface LearningDao {
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
    @Query("SELECT * FROM bookmarks ORDER BY updatedAt DESC, lessonId LIMIT 1") suspend fun latestBookmark(): BookmarkEntity?
    @Upsert suspend fun bookmark(value: BookmarkEntity)
    @Insert suspend fun attempt(value: AttemptEntity)
    @Query("SELECT payload FROM attempts WHERE lessonId = :lessonId ORDER BY recordedAt, id") suspend fun attempts(lessonId: String): List<String>
    @Upsert suspend fun repertoire(value: RepertoireEntity)
}

@Database(entities = [PackEntity::class, ActivePackEntity::class, InstallJobEntity::class,
    ContentRecordEntity::class, PositionEntity::class, BookmarkEntity::class, AttemptEntity::class,
    RepertoireEntity::class, AnnotationEntity::class], version = 2, exportSchema = true)
@ConstructedBy(LearningDatabaseConstructor::class)
abstract class LearningDatabase : RoomDatabase() {
    abstract fun learningDao(): LearningDao
    companion object {
        // v1 is a pre-release schema fixture, not a previously shipped production database.
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE packs ADD COLUMN notices TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object LearningDatabaseConstructor : RoomDatabaseConstructor<LearningDatabase> {
    override fun initialize(): LearningDatabase
}

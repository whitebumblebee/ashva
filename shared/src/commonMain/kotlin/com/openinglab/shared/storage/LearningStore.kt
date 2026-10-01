package com.openinglab.shared.storage

import com.openinglab.shared.content.ContentManifest
import com.openinglab.shared.content.GameRecord
import com.openinglab.shared.content.OpeningRecord
import com.openinglab.shared.lesson.ReplaySnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable data class LessonBookmark(
    val lessonId: String, val contentVersion: String, val replay: ReplaySnapshot,
    val mode: String, val mistakes: Int = 0, val assisted: Int = 0,
    val currentAssisted: Boolean = false, val studied: Boolean = false,
    val hint: Boolean = false, val offerBranches: Boolean = false,
    val attemptUci: String? = null, val promotionUci: String? = null, val speedMillis: Long = 1200,
)
@Serializable data class LearningAttempt(
    val id: String, val lessonId: String, val pathId: String, val ply: Int,
    val side: String, val moveUci: String, val outcome: String, val assisted: Boolean, val recordedAt: Long,
)
@Serializable data class RepertoireSelection(val lessonId: String, val side: String, val preferredPathId: String)
data class PackAvailability(
    val sourceId: String, val requestedPackId: String, val state: String, val error: String?,
    val activePackId: String?,
)
data class InstalledPack(val manifest: ContentManifest, val manifestSha256: String, val notices: String)

/** Bytes come from a bounded platform adapter; the expected hash comes from a trusted catalog. */
data class PackBundle(val manifest: ByteArray, val files: Map<String, ByteArray>, val expectedManifestSha256: String)

interface LearningStore {
    val availability: Flow<List<PackAvailability>>
    suspend fun recoverInterruptedInstalls()
    suspend fun install(bundle: PackBundle)
    suspend fun activePacks(): List<InstalledPack>
    suspend fun activatePreviousVersion(sourceId: String, packId: String)
    suspend fun openings(packId: String? = null): List<OpeningRecord>
    suspend fun openingIdsAt(positionKey: String): List<String>
    suspend fun game(id: String): GameRecord?
    suspend fun latestBookmark(): LessonBookmark?
    suspend fun saveBookmark(bookmark: LessonBookmark)
    suspend fun recordAttempt(attempt: LearningAttempt)
    suspend fun attempts(lessonId: String): List<LearningAttempt>
    suspend fun selectRepertoire(selection: RepertoireSelection)
    suspend fun repertoires(): List<RepertoireSelection>
}

expect fun contentSha256(bytes: ByteArray): String

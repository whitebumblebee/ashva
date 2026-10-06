package com.openinglab.shared.storage

import com.openinglab.shared.content.ContentManifest
import com.openinglab.shared.content.GameRecord
import com.openinglab.shared.content.OpeningRecord
import com.openinglab.shared.lesson.ReplaySnapshot
import com.openinglab.shared.repertoire.RepertoirePolicy
import com.openinglab.shared.repertoire.RepertoireSet
import com.openinglab.shared.games.FollowedPlayer
import com.openinglab.shared.games.PrivateGameRecord
import com.openinglab.shared.games.GameStudyReference
import com.openinglab.shared.review.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

@Serializable data class LessonBookmark(
    val lessonId: String, val contentVersion: String, val replay: ReplaySnapshot,
    val mode: String, val mistakes: Int = 0, val assisted: Int = 0,
    val currentAssisted: Boolean = false, val studied: Boolean = false,
    val hint: Boolean = false, val offerBranches: Boolean = false,
    val attemptUci: String? = null, val promotionUci: String? = null, val speedMillis: Long = 1200,
    val repertoireId: String? = null, val repertoireRevision: Int? = null,
    val repertoireSetId: String? = null, val repertoireSetRevision: Int? = null, val repertoireSetIndex: Int? = null,
    val gameReference: GameStudyReference? = null,
    val recallHelp: Set<RecallHelp> = emptySet(), val studyExposedAt: Long? = null,
    val reviewScopeId: String? = null, val reviewTargetId: String? = null, val reviewAnswered: Boolean = false,
    val reviewAttemptId: String? = null,
    val activitySessionId: String? = null,
    val practiceStartPly: Int = 0,
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
    fun learnerActivity(at: Long): Flow<com.openinglab.shared.practice.LearnerActivity> = flowOf(com.openinglab.shared.practice.LearnerActivity())
    suspend fun recordStudyActivity(activity: com.openinglab.shared.practice.StudyActivity) {}
    val tactics: com.openinglab.shared.tactics.TacticsStore? get() = null
    val availability: Flow<List<PackAvailability>>
    suspend fun recoverInterruptedInstalls()
    suspend fun install(bundle: PackBundle)
    suspend fun activePacks(): List<InstalledPack>
    suspend fun retainedPack(packId: String): InstalledPack? = activePacks().singleOrNull { it.manifest.packId == packId }
    suspend fun activatePreviousVersion(sourceId: String, packId: String)
    suspend fun openings(packId: String? = null): List<OpeningRecord>
    suspend fun openingIdsAt(positionKey: String): List<String>
    suspend fun game(id: String): GameRecord?
    /** Active game packs by default, or an exact retained immutable pack when supplied. */
    suspend fun games(packId: String? = null): List<GameRecord>
    /** Exact retained version for cold game bookmarks, even when a newer pack is active. */
    suspend fun gameInPack(packId: String, id: String): GameRecord? = games(packId).singleOrNull { it.id == id }
    val followedPlayers: Flow<List<FollowedPlayer>> get() = flowOf(emptyList())
    suspend fun followPlayer(player: FollowedPlayer) { throw UnsupportedOperationException("Player following is unavailable") }
    suspend fun unfollowPlayer(id: String) { throw UnsupportedOperationException("Player following is unavailable") }
    val privateGames: Flow<List<PrivateGameRecord>> get() = flowOf(emptyList())
    suspend fun privateGame(id: String): PrivateGameRecord? = null
    suspend fun importPrivateGame(text: String): PrivateGameRecord { throw UnsupportedOperationException("Private import is unavailable") }
    suspend fun latestBookmark(): LessonBookmark?
    suspend fun saveBookmark(bookmark: LessonBookmark)
    suspend fun recordAttempt(attempt: LearningAttempt)
    suspend fun attempts(lessonId: String): List<LearningAttempt>
    suspend fun selectRepertoire(selection: RepertoireSelection)
    suspend fun repertoires(): List<RepertoireSelection>
    val repertoirePolicies: Flow<List<RepertoirePolicy>>
    suspend fun repertoirePolicy(id: String, revision: Int? = null): RepertoirePolicy?
    /** Atomic next-revision save; old revisions remain available to existing bookmarks. */
    suspend fun saveRepertoirePolicy(policy: RepertoirePolicy)
    val repertoireSets: Flow<List<RepertoireSet>>
    suspend fun repertoireSet(id: String, revision: Int? = null): RepertoireSet?
    suspend fun saveRepertoireSet(set: RepertoireSet)
    /** Additive, immutable scopes. Previous content/policy revisions are never deleted. */
    val supportsRecall: Boolean get() = false
    fun recallScopes(at: Long): Flow<List<RecallScopeSummary>> = flowOf(emptyList())
    val learningTotals: Flow<LearningTotals> get() = flowOf(LearningTotals())
    suspend fun enrollRecall(enrollment: RecallEnrollment) { throw UnsupportedOperationException("Recall storage unavailable") }
    suspend fun recallCards(scopeId: String, at: Long, limit: Int = 50): List<RecallCard> = emptyList()
    suspend fun recallCard(scopeId: String, cardId: String): RecallCard? = null
    suspend fun hasRecallEvent(attemptId: String, cardId: String): Boolean = false
    suspend fun recordRecall(event: RecallEvent) { throw UnsupportedOperationException("Recall storage unavailable") }
    suspend fun recordStudyView(id: String, scopeId: String, at: Long) { throw UnsupportedOperationException("Recall storage unavailable") }
}

expect fun contentSha256(bytes: ByteArray): String

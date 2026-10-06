// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/** One learner flag on a generated explanation. Stored only on the device; exported only when the learner shares it. */
@Serializable
data class CourseFeedback(
    val courseLessonId: String,
    val lineId: String,
    val ply: Int,
    val san: String,
    val kind: String,
    val text: String,
    val label: String,
    val createdAtMillis: Long,
)

/**
 * Small app-private JSON file (not the Room learning database: flags are review input for regenerating course
 * text, not learning progress). Bounded, written atomically, excluded from backup like all app data.
 */
class CourseFeedbackStore(private val file: File) {
    private val codec = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val lock = Any()

    fun all(): List<CourseFeedback> = synchronized(lock) {
        if (!file.isFile) emptyList() else runCatching { codec.decodeFromString(ListSerializer(CourseFeedback.serializer()), file.readText()) }.getOrDefault(emptyList())
    }

    fun add(feedback: CourseFeedback): List<CourseFeedback> = synchronized(lock) {
        val updated = (all().filterNot { it.lineId == feedback.lineId && it.ply == feedback.ply && it.kind == feedback.kind } + feedback).takeLast(MAX)
        file.parentFile?.mkdirs()
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeText(codec.encodeToString(ListSerializer(CourseFeedback.serializer()), updated))
        require(temp.renameTo(file)) { "Feedback could not be saved." }
        updated
    }

    fun export(): String = codec.encodeToString(ListSerializer(CourseFeedback.serializer()), all())

    companion object { const val MAX = 500 }
}

// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.course

import kotlin.random.Random
import kotlin.math.round

/** A stable tree of indices into the pack; names do not determine ancestry. */
class CourseVariationTree(val variations: List<CourseVariation>) {
    private class Prefix {
        val children = mutableMapOf<String, Prefix>()
        var firstIndex: Int? = null
    }
    private val root = Prefix().also { root -> variations.forEachIndexed { index, v ->
        var node = root
        v.path.forEach { node = node.children.getOrPut(it) { Prefix() } }
        if (node.firstIndex == null) node.firstIndex = index
    } }
    val parents: List<Int?> = variations.map { child ->
        var node = root
        var parent: Int? = null
        child.path.forEach { move ->
            node.firstIndex?.let { parent = it }
            node = node.children.getValue(move)
        }
        parent
    }
    private val byParent = variations.indices.groupBy { parents[it] }
    fun children(parent: Int?): List<Int> = byParent[parent].orEmpty()
    fun shortName(index: Int): String {
        val name = variations[index].name
        val parent = parents[index]?.let { variations[it].name } ?: return name
        val suffix = name.removePrefix(parent)
        return if (suffix != name && suffix.firstOrNull() in listOf(' ', ',', '·', ':'))
            suffix.trimStart(' ', ',', '·', ':').ifBlank { name } else name
    }
}

/** Keep presentation names separate from immutable lesson identities and saved fingerprints. */
fun CourseChapter.displayTitle(course: DeepCoursePack): String = title.removePrefix("${course.title}: ")

/** Display-only summaries leave the retained lesson descriptions and fingerprints intact. */
internal fun friendlyLineEnding(verdict: Verdict?): String {
    val evaluation = verdict?.evalCp?.let { cp ->
        val value = round(cp / 10.0) / 10.0
        " (${if (value > 0) "+" else ""}${if (value == 0.0) 0.0 else value})"
    }.orEmpty()
    return when (verdict?.result) {
        "EQUAL" -> "Ends equal$evaluation"
        "WHITE_BETTER" -> "White ends better$evaluation"
        "BLACK_BETTER" -> "Black ends better$evaluation"
        "TRANSPOSES" -> {
            // Legacy packs encode the destination in their checked plan rather than a separate field.
            val target = Regex("^This position also arises by another move order \\((.+)\\)\\.")
                .find(verdict.whitePlan)?.groupValues?.get(1)
            "Joins ${target?.let(DeepCourseCatalog::shortName) ?: "another line"}"
        }
        else -> "Unclear"
    }
}

fun DeepCourseChapterView.weightedLineThrough(nodeId: String? = null, random: Random = Random.Default): String? {
    val candidates = if (nodeId == null) opening.variations.map { it.id } else linesByNode[nodeId].orEmpty()
    if (candidates.isEmpty()) return null
    val weighted = candidates.map { it to (lineWeights[it] ?: 0.0) }.filter { it.second > 0 }
    if (weighted.isEmpty()) return candidates.random(random)
    var roll = random.nextDouble() * weighted.sumOf { it.second }
    return weighted.firstOrNull { (_, weight) -> roll -= weight; roll <= 0 }?.first ?: weighted.last().first
}

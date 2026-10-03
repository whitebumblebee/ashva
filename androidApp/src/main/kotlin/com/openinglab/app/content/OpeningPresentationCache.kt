// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import com.openinglab.shared.data.SourcedOpeningCatalog
import com.openinglab.shared.data.TeachingCatalog
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class OpeningPresentation(val source: SourcedOpeningCatalog, val teaching: TeachingCatalog)

/** One immutable presentation per process, not a graph/learner cache. Exact manifest key only. */
class OpeningPresentationCache {
    private val mutex = Mutex()
    private var current: Pair<String, OpeningPresentation>? = null
    suspend fun get(key: String, load: suspend () -> OpeningPresentation): OpeningPresentation = mutex.withLock {
        current?.takeIf { it.first == key }?.second ?: load().also { current = key to it }
    }
}

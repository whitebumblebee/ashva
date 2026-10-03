// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Clearing a ViewModel requests cancellation, but does not join worker jobs.
 * A test must await their completion before closing its private SQLite handle
 * or recreating a cold model. Never block Main while joining Main-scoped jobs.
 */
internal fun clearTestViewModels(vararg owners: ViewModelStore, onMain: (() -> Unit) -> Unit) {
    var jobs: List<Job> = emptyList()
    onMain {
        jobs = owners.flatMap { owner ->
            owner.keys().mapNotNull { key -> owner[key]?.viewModelScope?.coroutineContext?.get(Job) }
        }
        owners.forEach(ViewModelStore::clear)
    }
    runBlocking { withTimeout(10_000) { jobs.joinAll() } }
}

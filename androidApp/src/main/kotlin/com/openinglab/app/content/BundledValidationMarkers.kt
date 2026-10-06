// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import android.content.SharedPreferences
import android.annotation.SuppressLint
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import com.openinglab.shared.data.PackValidationCache
import com.openinglab.shared.data.PackValidationIdentity

/** App-private, disposable validation proofs; no learner rows or saved lesson identities. */
class BundledValidationMarkers(
    private val preferences: SharedPreferences,
    sha256: String,
    versionCode: Int,
    validatorVersion: Int,
) : PackValidationCache {
    private val identity = PackValidationIdentity(sha256, versionCode, validatorVersion)
    override fun isValidated(section: String): Boolean = preferences.getBoolean(identity.key(section), false)
    @WorkerThread
    @SuppressLint("ApplySharedPref") // Synchronous durability after success; this method runs on a worker.
    override fun markValidated(section: String) {
        // Called only on a worker after success; a failed disk write simply causes revalidation.
        preferences.edit(commit = true) { putBoolean(identity.key(section), true) }
    }
}

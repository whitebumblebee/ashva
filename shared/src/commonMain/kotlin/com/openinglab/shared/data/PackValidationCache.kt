// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.data

/**
 * Proof of successful validation of immutable bytes. The caller must verify the pack checksum on
 * every read and scope this cache to that checksum, app version and validator version. Never use
 * markers for mutable imports, learner data or content whose identity has not been verified.
 */
interface PackValidationCache {
    fun isValidated(section: String): Boolean
    fun markValidated(section: String)
}

/** Exact invalidation key shared by the runtime and host regression tests. */
data class PackValidationIdentity(val sha256: String, val appVersionCode: Int, val validatorVersion: Int) {
    init {
        require(sha256.length == 64 && sha256.all { it in 'a'..'f' || it in '0'..'9' })
        require(appVersionCode > 0 && validatorVersion > 0)
    }
    fun key(section: String): String = "$sha256:$appVersionCode:$validatorVersion:$section"
}

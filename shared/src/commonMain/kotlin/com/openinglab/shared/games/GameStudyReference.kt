// SPDX-License-Identifier: Apache-2.0
package com.openinglab.shared.games

import com.openinglab.shared.storage.contentSha256
import kotlinx.serialization.Serializable

/** A retained exact source, not an active-pack pointer; private imports have no public pack license. */
@Serializable
data class GameStudyReference(
    val recordId: String,
    val packId: String? = null,
    val manifestSha256: String? = null,
    val privateImport: Boolean = false,
) {
    val lessonId: String get() = "game:v1:" + contentSha256(
        "$privateImport\n${packId.orEmpty()}\n${manifestSha256.orEmpty()}\n$recordId".encodeToByteArray())
    fun validate() {
        require(recordId.length in 1..160 && recordId.none { it.isISOControl() })
        if (privateImport) require(packId == null && manifestSha256 == null && recordId.startsWith("private-game:"))
        else require(packId?.matches(Regex("[a-z0-9][a-z0-9.-]{0,199}")) == true && manifestSha256?.matches(Regex("[a-f0-9]{64}")) == true)
    }
}

// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.tactics

import android.content.res.AssetManager
import com.openinglab.shared.storage.contentSha256
import java.io.ByteArrayOutputStream

object BundledTactics {
    const val PATH = "content/tactics/v1/tactics.json"
    const val TRUSTED_SHA256 = "4cf96253712fbd04e551df12a127d854efccde9e5a0edc9695e10e109ed0e613"
    fun read(assets: AssetManager, expectedSha256: String = TRUSTED_SHA256): String {
        require(expectedSha256.matches(Regex("[a-f0-9]{64}"))) { "Tactics pack failed its checks" }
        val bytes = assets.open(PATH).use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(out.size() + n <= 1_500_000) { "Tactics pack failed its checks" }
                out.write(buffer, 0, n)
            }; out.toByteArray()
        }
        require(contentSha256(bytes) == expectedSha256) { "Tactics pack failed its checks" }
        return bytes.decodeToString()
    }
}

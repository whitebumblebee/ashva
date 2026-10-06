// SPDX-License-Identifier: Apache-2.0
package com.openinglab.app.content

import android.content.res.AssetManager
import com.openinglab.shared.storage.contentSha256
import java.io.ByteArrayOutputStream

/**
 * Deep course packs bundled as read-only assets (docs/DEEP_COURSE_PLAN.md). The trusted hash is copied from the
 * pipeline's course.manifest.json; a mismatch fails closed instead of showing unchecked text.
 */
object BundledCourses {
    const val PATH = "content/courses/ruy-lopez/v1/course.json"
    const val TRUSTED_SHA256 = "7437b57ef044c4d3a42cbad45044095db291cf72b65cf803175b6c8b2243fa58"
    private const val LIMIT = 16 * 1024 * 1024

    fun read(assets: AssetManager): String {
        val bytes = assets.open(PATH).use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= LIMIT) { "Bundled course exceeds its size limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        require(contentSha256(bytes) == TRUSTED_SHA256) { "Bundled course does not match its reviewed checksum." }
        return bytes.decodeToString()
    }
}

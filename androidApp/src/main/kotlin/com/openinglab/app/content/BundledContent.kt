package com.openinglab.app.content

import android.content.res.AssetManager
import com.openinglab.shared.storage.PackBundle
import java.io.ByteArrayOutputStream

data class ContentPackChoice(val sourceId: String, val packId: String, val title: String, val summary: String,
    val license: String, val manifestSha256: String)

/** Hashes were reviewed against the immutable pipeline output, not obtained from the incoming pack. */
object BundledContent {
    val choices = listOf(
        ContentPackChoice("lichess-openings", "lichess-openings-c67912be581f-import-v1", "Opening names & routes",
            "3,815 taxonomy sequences · 149 name-derived families · 1–36 half-moves. Names and legal routes, not full teaching repertoires.",
            "CC0-1.0", "40d2ab2920dafa5c60ce163695c8e0e3fa31cf1cf922a285461aeafcb1f55dc3"),
        ContentPackChoice("lichess-broadcast-2020-04", "lichess-broadcast-2020-04-2020-04-snap-import-v1", "April 2020 broadcast scores",
            "79 archived scores · 7,606 half-moves. Install opening names first. Source-reported players; not a verified GM collection or annotated lessons.",
            "CC-BY-SA-4.0", "cf50ffff258582121d7a8f4968ffd4cb60e10ba04ea1a8fa5eb69aa51947ffc7"),
        ContentPackChoice("lichess-broadcast-2020-01", "lichess-broadcast-2020-01-2020-01-snap-import-v1", "January 2020 broadcast scores",
            "857 accepted scores · 75,073 half-moves · 1–253 per score. All 952 inputs attempted; 95 quarantined (55 unfinished, 40 invalid). Install opening names first. Includes source-reported Tata Steel, Gibraltar and Women's World Championship events; not complete GM careers or representative popularity.",
            "CC-BY-SA-4.0", "98c81851574b0d94f43b8a382e6a92df19328db2b8ced7e48d5c25ab50ff4c63"),
    )
    fun read(assets: AssetManager, choice: ContentPackChoice): PackBundle {
        fun bytes(name: String, limit: Int): ByteArray = assets.open("content/${choice.packId}/$name").use {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = it.read(buffer)
                if (count < 0) break
                require(output.size() + count <= limit) { "Bundled file exceeds installation limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        return PackBundle(bytes("manifest.json", 65_536),
            listOf("openings.jsonl", "games.jsonl", "issues.jsonl", "ATTRIBUTION.txt").associateWith { bytes(it, 8_388_608) },
            choice.manifestSha256)
    }
}

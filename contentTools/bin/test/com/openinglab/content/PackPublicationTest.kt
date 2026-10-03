package com.openinglab.content

import java.nio.file.Files
import kotlin.test.*

class PackPublicationTest {
    private fun inTemporaryDirectory(test: (java.nio.file.Path) -> Unit) {
        val root = Files.createTempDirectory("opening-lab-pack-test-")
        try { test(root) }
        finally {
            // Only this newly created test directory; never recurse over project/user content.
            Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        }
    }

    @Test fun identicalPublicationIsIdempotentAndDifferentContentCannotOverwriteIt() = inTemporaryDirectory { root ->
        val directory = root.resolve("pack-v1")
        val original = mapOf("records.jsonl" to "original".toByteArray())
        publishOrVerify(directory, original, false)
        publishOrVerify(directory, original, false)
        publishOrVerify(directory, original, true)
        assertFailsWith<IllegalArgumentException> { publishOrVerify(directory, mapOf("records.jsonl" to "different".toByteArray()), false) }
        assertEquals("original", Files.readString(directory.resolve("records.jsonl")))
        assertEquals(listOf("pack-v1"), Files.list(root).use { paths -> paths.map { it.fileName.toString() }.toList() })
    }

    @Test fun missingAndUnexpectedPackFilesAreNotSilentlyAccepted() = inTemporaryDirectory { root ->
        val directory = root.resolve("pack-v1")
        val payload = mapOf("records.jsonl" to byteArrayOf())
        assertFailsWith<IllegalArgumentException> { publishOrVerify(directory, payload, true) }
        assertFalse(Files.exists(directory))
        publishOrVerify(directory, payload, false)
        Files.writeString(directory.resolve("unexpected.txt"), "extra")
        assertFailsWith<IllegalArgumentException> { publishOrVerify(directory, payload, true) }
    }

    @Test fun sourceHashAndSizeTamperingIsDetected() = inTemporaryDirectory { root ->
        val path = root.resolve("input.tsv")
        Files.writeString(path, "abc")
        verifyFile(path, 3, digest("abc"), 10)
        assertFailsWith<IllegalArgumentException> { verifyFile(path, 2, digest("abc"), 10) }
        assertFailsWith<IllegalArgumentException> { verifyFile(path, 3, digest("xyz"), 10) }
        assertFailsWith<IllegalArgumentException> { verifyFile(path, 3, digest("abc"), 2) }
    }

    @Test fun unsafePayloadNamesAreRejectedBeforeWriting() = inTemporaryDirectory { root ->
        assertFailsWith<IllegalArgumentException> { publishOrVerify(root.resolve("pack-v1"), mapOf("../escape" to byteArrayOf()), false) }
        assertFalse(Files.exists(root.resolve("pack-v1")))
    }
}

package com.openinglab.shared.storage

import kotlin.test.Test
import kotlin.test.assertEquals

class ContentHashTest {
    @Test fun sha256ReferenceVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", contentSha256(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", contentSha256("abc".encodeToByteArray()))
    }
}

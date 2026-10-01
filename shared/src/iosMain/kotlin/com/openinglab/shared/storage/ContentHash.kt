package com.openinglab.shared.storage

import kotlinx.cinterop.*
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH

@OptIn(ExperimentalForeignApi::class)
actual fun contentSha256(bytes: ByteArray): String = memScoped {
    val output = allocArray<UByteVar>(CC_SHA256_DIGEST_LENGTH)
    bytes.usePinned { pinned -> CC_SHA256(if (bytes.isEmpty()) null else pinned.addressOf(0), bytes.size.convert(), output) }
    (0 until CC_SHA256_DIGEST_LENGTH).joinToString("") { output[it].toString(16).padStart(2, '0') }
}

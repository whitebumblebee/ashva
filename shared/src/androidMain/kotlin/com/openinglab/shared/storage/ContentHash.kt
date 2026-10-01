package com.openinglab.shared.storage

import java.security.MessageDigest

actual fun contentSha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }

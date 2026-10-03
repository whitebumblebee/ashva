// SPDX-License-Identifier: Apache-2.0
package com.openinglab.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

data class ProviderReply(val status: Int, val retryAfter: String? = null, val bytes: ByteArray = byteArrayOf())

/** No provider is enabled here. A reviewed adapter must supply requests, body/rights checks and URLs. */
class ProviderBackoff(private val intervalMillis: Long = 1000,
    private val now: () -> Long = System::currentTimeMillis,
    private val wait: suspend (Long) -> Unit = { delay(it) }) {
    private val serial = Mutex()
    private var nextAllowed = 0L
    init { require(intervalMillis in 1000..60_000) }
    suspend fun request(send: suspend () -> ProviderReply): ProviderReply = serial.withLock {
        repeat(3) { attempt ->
            val delay = (nextAllowed - now()).coerceAtLeast(0)
            if (delay > 0) wait(delay)
            nextAllowed = now() + intervalMillis
            val response = send()
            require(response.bytes.size <= 8_388_608)
            require(response.retryAfter == null || response.retryAfter.length <= 200)
            if (response.status != 429 && response.status !in 500..599) return@withLock response
            if (attempt == 2) return@withLock response
            val headerDelay = response.retryAfter?.let { value ->
                value.toLongOrNull()?.takeIf { it >= 0 }?.let { seconds ->
                    if (seconds > 86400) throw ApiFailure("PROVIDER_RETRY_DEFERRED", 503)
                    seconds * 1000
                }
                    ?: runCatching { (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now()).coerceAtLeast(0) }.getOrNull()
            }
            // Never clamp a provider's long Retry-After into an earlier retry.
            if (headerDelay != null && headerDelay > 24L * 3600 * 1000) throw ApiFailure("PROVIDER_RETRY_DEFERRED", 503)
            val requestedDelay = headerDelay ?: (1000L shl (attempt + 1))
            nextAllowed = maxOf(nextAllowed, now() + if (response.status == 429) maxOf(60_000, requestedDelay) else requestedDelay)
        }
        error("Unreachable")
    }
}

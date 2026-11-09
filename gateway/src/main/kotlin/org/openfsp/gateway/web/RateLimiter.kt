package org.openfsp.gateway.web

import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

class RateLimiter(
    private val ratePerSecond: Double,
    private val burst: Double,
    private val clock: Clock,
) {
    private data class Bucket(
        val tokens: Double,
        val at: Long,
    )

    private val buckets = ConcurrentHashMap<String, Bucket>()

    fun acquire(key: String): Duration? {
        var wait: Duration? = null
        buckets.compute(key) { _, current ->
            val now = clock.millis()
            val refilled = current?.let { min(burst, it.tokens + (now - it.at) / MILLIS * ratePerSecond) } ?: burst
            if (refilled >= 1) {
                Bucket(refilled - 1, now)
            } else {
                wait = Duration.ofSeconds(ceil((1 - refilled) / ratePerSecond).toLong().coerceAtLeast(1))
                Bucket(refilled, now)
            }
        }
        if (buckets.size > MAX_KEYS) buckets.entries.removeIf { clock.millis() - it.value.at > IDLE_MILLIS }
        return wait
    }

    private companion object {
        const val MILLIS = 1000.0
        const val MAX_KEYS = 100_000
        const val IDLE_MILLIS = 600_000L
    }
}

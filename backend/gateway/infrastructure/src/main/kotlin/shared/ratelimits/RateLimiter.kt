package org.openfsp.gateway.shared.ratelimits

import java.time.Clock
import java.time.Duration
import kotlin.math.ceil
import kotlin.math.min

class RateLimiter(
    private val ratePerSecond: Double,
    private val burst: Double,
    private val clock: Clock,
    private val capacity: Int = MAX_KEYS,
) {
    private data class Bucket(
        val tokens: Double,
        val at: Long,
    )

    private val buckets = LinkedHashMap<String, Bucket>(INITIAL_CAPACITY, LOAD_FACTOR, true)

    internal val size: Int get() = synchronized(buckets) { buckets.size }

    fun acquire(key: String): Duration? =
        synchronized(buckets) {
            val now = clock.millis()
            val current = buckets[key]
            if (current == null && buckets.size >= capacity) dropLeastRecentlyUsed()
            val refilled = current?.let { refill(it, now) } ?: burst
            if (refilled >= 1) {
                buckets[key] = Bucket(refilled - 1, now)
                null
            } else {
                buckets[key] = Bucket(refilled, now)
                secondsUntil(1 - refilled)
            }
        }

    private fun dropLeastRecentlyUsed() {
        val eldest = buckets.entries.iterator()
        eldest.next()
        eldest.remove()
    }

    private fun refill(
        bucket: Bucket,
        now: Long,
    ): Double = min(burst, bucket.tokens + (now - bucket.at) / MILLIS * ratePerSecond)

    private fun secondsUntil(missing: Double): Duration = Duration.ofSeconds(ceil(missing / ratePerSecond).toLong().coerceAtLeast(1))

    private companion object {
        const val MILLIS = 1000.0
        const val MAX_KEYS = 100_000
        const val INITIAL_CAPACITY = 1024
        const val LOAD_FACTOR = 0.75f
    }
}

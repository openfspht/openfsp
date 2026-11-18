package org.openfsp.simulator.emission

import java.time.Duration

object RetrySchedule {
    private const val JITTER_MIN = 0.2
    private const val JITTER_SPAN = 0.2
    private val HONORS_RETRY_AFTER = setOf(429, 503)
    private val MAX_RETRY_AFTER: Duration = Duration.ofHours(1)
    val DELAYS: List<Duration> =
        listOf(
            Duration.ofSeconds(30),
            Duration.ofMinutes(2),
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            Duration.ofHours(6),
            Duration.ofHours(24),
        )

    fun delayAfter(
        attempt: Int,
        result: DeliveryResult,
        jitter: Double,
    ): Duration? {
        if (result.succeeded || result.gone) return null
        val base = DELAYS.getOrNull(attempt - 1) ?: return null
        val jittered = base.plusMillis((base.toMillis() * (JITTER_MIN + jitter.coerceIn(0.0, 1.0) * JITTER_SPAN)).toLong())
        val asked = result.retryAfter?.takeIf { result.status in HONORS_RETRY_AFTER }?.coerceAtMost(MAX_RETRY_AFTER)
        return asked?.coerceAtLeast(jittered) ?: jittered
    }
}

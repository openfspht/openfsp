package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import java.security.SecureRandom
import java.time.Duration

sealed interface DeliveryDecision {
    data object Succeeded : DeliveryDecision

    data object Stopped : DeliveryDecision

    data object Abandoned : DeliveryDecision

    data class RetryIn(
        val delay: Duration,
    ) : DeliveryDecision
}

@DomainService
class DeliveryPolicy {
    private val random = SecureRandom()

    fun decide(
        attempt: Int,
        result: TransportResult,
    ): DeliveryDecision {
        val status = result.statusCode
        return when {
            status != null && status in SUCCESS -> DeliveryDecision.Succeeded
            status == GONE -> DeliveryDecision.Stopped
            attempt >= SCHEDULE.size -> DeliveryDecision.Abandoned
            else -> DeliveryDecision.RetryIn(delayAfter(attempt, result.retryAfter))
        }
    }

    fun delayAfter(
        attempt: Int,
        retryAfter: Duration?,
    ): Duration {
        val base = SCHEDULE[attempt]
        val jittered = base.plusMillis((base.toMillis() * (JITTER_MIN + random.nextDouble() * JITTER_SPAN)).toLong())
        return retryAfter?.coerceAtMost(MAX_RETRY_AFTER)?.coerceAtLeast(jittered) ?: jittered
    }

    companion object {
        private val SUCCESS = 200..299
        private const val GONE = 410
        private const val JITTER_MIN = 0.2
        private const val JITTER_SPAN = 0.2
        private val MAX_RETRY_AFTER: Duration = Duration.ofHours(1)
        val SCHEDULE: List<Duration> =
            listOf(
                Duration.ZERO,
                Duration.ofSeconds(30),
                Duration.ofMinutes(2),
                Duration.ofMinutes(10),
                Duration.ofHours(1),
                Duration.ofHours(6),
                Duration.ofHours(24),
            )
    }
}

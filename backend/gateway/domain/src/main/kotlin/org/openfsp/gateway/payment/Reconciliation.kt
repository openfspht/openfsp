package org.openfsp.gateway.payment

import java.time.Duration
import java.time.Instant

data class ReconciliationDue(
    val providers: Set<String>,
    val now: Instant,
    val quietBefore: Instant,
    val inFlightSince: Instant,
    val claimedUntil: Instant,
)

data class ReconciliationTurn(
    val payment: Payment,
    val attempts: Int,
) {
    val nextAttempts: Int get() = attempts + 1

    val delayAfter: Duration get() = minOf(FIRST_DELAY.multipliedBy(1L shl attempts.coerceIn(0, MAX_DOUBLINGS)), MAX_DELAY)

    companion object {
        val FIRST_DELAY: Duration = Duration.ofSeconds(30)
        val MAX_DELAY: Duration = Duration.ofHours(1)
        val CLAIM: Duration = Duration.ofMinutes(5)
        private const val MAX_DOUBLINGS = 7
    }
}

package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.server.webhook.DeliverySchedule
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
            else -> DeliverySchedule.delayAfter(attempt, status, result.retryAfter, random.nextDouble())?.let(DeliveryDecision::RetryIn) ?: DeliveryDecision.Abandoned
        }
    }

    private companion object {
        val SUCCESS = 200..299
        const val GONE = 410
    }
}

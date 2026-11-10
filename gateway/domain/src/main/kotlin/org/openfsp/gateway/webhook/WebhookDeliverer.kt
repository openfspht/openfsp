package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.webhook.api.DeliverWebhooks
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.gateway.webhook.spi.WebhookTransport
import java.time.Duration

@DomainService
class WebhookDeliverer(
    private val deliveries: Deliveries,
    private val transport: WebhookTransport,
    private val health: EndpointHealth,
    private val policy: DeliveryPolicy,
    private val transactions: Transactions,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
    private val limits: DeliveryLimits,
) : DeliverWebhooks {
    override fun claimDue(limit: Int): List<Delivery> = transactions.inTransaction { deliveries.claimDue(calendar.now(), LEASE, limit) }

    override fun deliver(delivery: Delivery) {
        val attempt = delivery.nextAttempt
        val startedAt = calendar.now()
        val result = transport.send(delivery, attempt)
        transactions.inTransaction {
            deliveries.record(DeliveryAttempt(delivery.id, attempt, startedAt, result.statusCode, result.error, result.durationMillis))
            when (val decision = policy.decide(attempt, result)) {
                DeliveryDecision.Succeeded -> {
                    deliveries.finish(delivery.id, SUCCEEDED, attempt, calendar.now())
                    health.succeeded(delivery.endpoint)
                }
                DeliveryDecision.Stopped -> {
                    deliveries.finish(delivery.id, STOPPED, attempt, calendar.now())
                    disable(delivery.endpoint, "answered 410 Gone for event ${delivery.eventId}")
                }
                DeliveryDecision.Abandoned -> {
                    deliveries.finish(delivery.id, ABANDONED, attempt, calendar.now())
                    signals.signal(delivery.endpoint, "event ${delivery.eventId} abandoned after $attempt attempts")
                    if (health.abandoned(delivery.endpoint) >= limits.disableAfterAbandoned) {
                        disable(delivery.endpoint, "${limits.disableAfterAbandoned} deliveries abandoned in a row")
                    }
                }
                is DeliveryDecision.RetryIn -> deliveries.retry(delivery.id, attempt, calendar.now().plus(decision.delay))
            }
        }
    }

    private fun disable(
        endpoint: String,
        reason: String,
    ) {
        health.disable(endpoint, reason, calendar.now())
        signals.signal(endpoint, "disabled: $reason")
    }

    private companion object {
        const val SUCCEEDED = "succeeded"
        const val STOPPED = "stopped"
        const val ABANDONED = "abandoned"
        val LEASE: Duration = Duration.ofMinutes(1)
    }
}

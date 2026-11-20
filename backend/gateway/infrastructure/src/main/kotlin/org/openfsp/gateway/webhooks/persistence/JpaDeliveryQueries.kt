package org.openfsp.gateway.webhooks.persistence

import org.openfsp.gateway.webhook.AttemptActivity
import org.openfsp.gateway.webhook.EndpointCondition
import org.openfsp.gateway.webhook.RecordedAttempt
import org.openfsp.gateway.webhook.RecordedDelivery
import org.openfsp.gateway.webhook.RecordedEvent
import org.openfsp.gateway.webhook.spi.DeliveryQueries
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component

@Component
class JpaDeliveryQueries(
    private val events: EventJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val attempts: AttemptJpaRepository,
    private val states: EndpointStateJpaRepository,
) : DeliveryQueries {
    override fun retryingCount(): Long = deliveries.countByStatusAndAttemptsGreaterThan(PENDING, 0)

    override fun disabledEndpointCount(): Long = states.countByDisabledAtIsNotNull()

    override fun conditionOf(endpoint: String): EndpointCondition? = states.findById(endpoint).orElse(null)?.let { EndpointCondition(it.disabledAt, it.reason) }

    override fun eventsOf(paymentId: String): List<RecordedEvent> {
        val recorded = events.findByPaymentIdOrderBySequence(paymentId)
        val queued = deliveries.findByEventIdInOrderById(recorded.map { it.id })
        val tried = attempts.findByDeliveryIdInOrderById(queued.mapNotNull { it.id }).groupBy { it.deliveryId }
        val byEvent = queued.groupBy({ it.eventId }) { RecordedDelivery(it.endpoint, it.url, it.status, tried[it.id].orEmpty().map(::recorded), it.nextAttemptAt) }
        return recorded.map { RecordedEvent(it.id, it.type, it.sequence, it.createdAt, byEvent[it.id].orEmpty()) }
    }

    override fun latestAttempts(limit: Int): List<AttemptActivity> {
        val recent = attempts.findByOrderByIdDesc(Limit.of(limit))
        val deliveryById = deliveries.findAllById(recent.map { it.deliveryId }.toSet()).associateBy { it.id!! }
        val eventById = events.findAllById(deliveryById.values.map { it.eventId }.toSet()).associateBy { it.id }
        return recent.mapNotNull { attempt ->
            val delivery = deliveryById[attempt.deliveryId] ?: return@mapNotNull null
            val event = eventById[delivery.eventId] ?: return@mapNotNull null
            AttemptActivity(recorded(attempt), delivery.endpoint, event.type, event.paymentId)
        }
    }

    override fun countAttemptsUpTo(cap: Int): Long = attempts.countUpTo(cap)

    private fun recorded(attempt: AttemptJpaEntity) = RecordedAttempt(attempt.id!!, attempt.attempt, attempt.startedAt, attempt.statusCode, attempt.error, attempt.durationMs)

    private companion object {
        const val PENDING = "pending"
    }
}

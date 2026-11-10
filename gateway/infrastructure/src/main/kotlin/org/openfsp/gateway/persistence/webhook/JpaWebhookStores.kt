package org.openfsp.gateway.persistence.webhook

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.DeliveryAttempt
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.gateway.webhook.spi.Events
import org.openfsp.gateway.webhooks.EventBodyMapper
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Duration
import java.time.Instant

@Component
class JpaEvents(
    private val repository: EventJpaRepository,
    private val bodies: EventBodyMapper,
) : Events {
    override fun nextSequence(paymentId: String): Int = repository.lastSequence(paymentId) + 1

    override fun save(event: WebhookEvent) {
        repository.save(
            EventJpaEntity(event.id, event.payment.id, event.type, event.sequence, event.previousStatus?.wire, event.createdAt, bodies.toJson(event)),
        )
    }
}

@Component
class JpaDeliveries(
    private val deliveries: DeliveryJpaRepository,
    private val events: EventJpaRepository,
    private val attempts: AttemptJpaRepository,
) : Deliveries {
    override fun enqueue(
        eventId: String,
        subscription: Subscription,
        at: Instant,
    ) {
        deliveries.save(DeliveryJpaEntity(eventId, subscription.name, subscription.url.toString(), PENDING, 0, at, at))
    }

    override fun pending(endpoint: String): Long = deliveries.countByEndpointAndStatus(endpoint, PENDING)

    override fun claimDue(
        now: Instant,
        lease: Duration,
        limit: Int,
    ): List<Delivery> {
        val due = deliveries.lockDue(now, Limit.of(limit))
        if (due.isEmpty()) return emptyList()
        val byId = events.findAllById(due.map { it.eventId }).associateBy { it.id }
        return due.map { delivery ->
            delivery.nextAttemptAt = now.plus(lease)
            val event = byId.getValue(delivery.eventId)
            Delivery(delivery.id!!, event.id, event.type, delivery.endpoint, URI(delivery.url), delivery.attempts, event.body)
        }
    }

    override fun record(attempt: DeliveryAttempt) {
        attempts.save(
            AttemptJpaEntity(attempt.deliveryId, attempt.attempt, attempt.startedAt, attempt.statusCode, attempt.error, attempt.durationMillis.toInt()),
        )
    }

    override fun finish(
        deliveryId: Long,
        status: String,
        attempts: Int,
        at: Instant,
    ) = update(deliveryId) {
        it.status = status
        it.attempts = attempts
        it.nextAttemptAt = null
        it.updatedAt = at
    }

    override fun retry(
        deliveryId: Long,
        attempts: Int,
        at: Instant,
    ) = update(deliveryId) {
        it.attempts = attempts
        it.nextAttemptAt = at
        it.updatedAt = at
    }

    private fun update(
        deliveryId: Long,
        change: (DeliveryJpaEntity) -> Unit,
    ) {
        deliveries.findById(deliveryId).ifPresent { deliveries.save(it.also(change)) }
    }

    private companion object {
        const val PENDING = "pending"
    }
}

@Component
class JpaEndpointHealth(
    private val states: EndpointStateJpaRepository,
) : EndpointHealth {
    override fun isDisabled(endpoint: String): Boolean = states.findById(endpoint).map { it.disabledAt != null }.orElse(false)

    override fun succeeded(endpoint: String) {
        states.findById(endpoint).ifPresent { states.save(it.also { state -> state.consecutiveAbandoned = 0 }) }
    }

    override fun abandoned(endpoint: String): Int {
        val state = states.findById(endpoint).orElseGet { EndpointStateJpaEntity(endpoint) }
        state.consecutiveAbandoned += 1
        return states.save(state).consecutiveAbandoned
    }

    override fun disable(
        endpoint: String,
        reason: String,
        at: Instant,
    ) {
        val state = states.findById(endpoint).orElseGet { EndpointStateJpaEntity(endpoint) }
        if (state.disabledAt != null) return
        state.disabledAt = at
        state.reason = reason
        states.save(state)
    }

    fun enable(endpoint: String) {
        states.findById(endpoint).ifPresent {
            it.disabledAt = null
            it.reason = null
            it.consecutiveAbandoned = 0
            states.save(it)
        }
    }

    fun state(endpoint: String): EndpointStateJpaEntity? = states.findById(endpoint).orElse(null)
}

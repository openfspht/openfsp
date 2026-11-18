package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.webhook.api.RecordPaymentEvents
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.gateway.webhook.spi.Events
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.protocol.ResourceIds

@DomainService
class PaymentEventRecorder(
    private val payments: Payments,
    private val events: Events,
    private val subscriptions: Subscriptions,
    private val deliveries: Deliveries,
    private val health: EndpointHealth,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
    private val limits: DeliveryLimits,
) : RecordPaymentEvents {
    override fun record(transition: PaymentTransitioned) {
        val payment = payments.find(transition.paymentId) ?: error("payment ${transition.paymentId} is not recorded")
        val now = calendar.now()
        val event =
            WebhookEvent(
                id = ResourceIds.generate("evt"),
                type = WebhookEvent.typeFor(transition.previous, transition.current),
                createdAt = now,
                sequence = events.nextSequence(payment.id),
                previousStatus = transition.previous,
                payment = payment,
            )
        events.save(event)
        subscriptions
            .all()
            .filter { it.wants(event.type) && it.follows(payment) && accepts(it) }
            .forEach { deliveries.enqueue(event.id, it, now) }
    }

    private fun accepts(subscription: Subscription): Boolean {
        if (health.isDisabled(subscription.name)) return false
        val pending = deliveries.pending(subscription.name)
        if (pending < limits.maxPendingPerEndpoint) return true
        signals.signal(subscription.name, "delivery queue full with $pending pending: new deliveries are shed, events are kept")
        return false
    }
}

data class DeliveryLimits(
    val maxPendingPerEndpoint: Int = 10_000,
    val disableAfterAbandoned: Int = 3,
)

package org.openfsp.gateway.admin.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.webhooks.persistence.AttemptJpaRepository
import org.openfsp.gateway.webhooks.persistence.DeliveryJpaRepository
import org.openfsp.gateway.webhooks.persistence.EventJpaRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class PaymentDetails(
    private val payments: Payments,
    private val journal: PaymentJournal,
    private val providers: Providers,
    private val events: EventJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val attempts: AttemptJpaRepository,
    private val resources: PaymentResourceMapper,
) {
    @Transactional(readOnly = true)
    fun of(paymentId: String): PaymentDetailResource {
        val payment = payments.find(paymentId) ?: throw OpenFspException.notFound()
        val recorded = events.findByPaymentIdOrderBySequence(paymentId)
        val queued = deliveries.findByEventIdInOrderById(recorded.map { it.id })
        val tried = attempts.findByDeliveryIdInOrderById(queued.mapNotNull { it.id }).groupBy { it.deliveryId }
        val byEvent =
            queued.groupBy({ it.eventId }) { delivery ->
                DeliveryResource(
                    delivery.endpoint,
                    delivery.url,
                    delivery.status,
                    tried[delivery.id].orEmpty().map { AttemptResource(it.attempt, it.startedAt, it.statusCode, it.error, it.durationMs) },
                    delivery.nextAttemptAt,
                )
            }
        return PaymentDetailResource(
            payment = resources.toResource(payment),
            automaticSources = providers[payment.provider]?.descriptor?.let { it.hasLookup || it.hasSignedCallbacks } ?: false,
            indeterminate = journal.indeterminate(paymentId).map { it.error },
            transitions = journal.transitions(paymentId).map { TransitionResource(it.from, it.to, it.source.wire, it.at) },
            conflicts =
                journal.conflicts(paymentId).map {
                    ConflictResource(it.source.wire, mapOf("status" to it.reportedStatus.wire, "amount" to it.reportedAmount.amount), it.at)
                },
            events = recorded.map { EventResource(it.id, it.type, it.sequence, it.createdAt, byEvent[it.id].orEmpty()) },
        )
    }
}

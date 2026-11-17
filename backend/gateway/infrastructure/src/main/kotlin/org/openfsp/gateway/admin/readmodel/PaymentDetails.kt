package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payments.persistence.ConflictJpaRepository
import org.openfsp.gateway.payments.persistence.IndeterminateOperationJpaRepository
import org.openfsp.gateway.payments.persistence.PaymentEntityMapper
import org.openfsp.gateway.payments.persistence.PaymentJpaRepository
import org.openfsp.gateway.payments.persistence.TransitionJpaRepository
import org.openfsp.gateway.payments.resources.PaymentResourceMapper
import org.openfsp.gateway.webhooks.persistence.AttemptJpaRepository
import org.openfsp.gateway.webhooks.persistence.DeliveryJpaRepository
import org.openfsp.gateway.webhooks.persistence.EventJpaRepository
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class PaymentDetails(
    private val payments: PaymentJpaRepository,
    private val entities: PaymentEntityMapper,
    private val transitions: TransitionJpaRepository,
    private val conflicts: ConflictJpaRepository,
    private val indeterminate: IndeterminateOperationJpaRepository,
    private val events: EventJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val attempts: AttemptJpaRepository,
    private val capabilities: DescribeCapabilities,
    private val resources: PaymentResourceMapper,
    private val json: JsonMapper,
) {
    @Transactional(readOnly = true)
    fun of(paymentId: String): PaymentDetailResource {
        val payment = payments.findById(paymentId).map(entities::toDomain).orElseThrow { OpenFspException.notFound() }
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
            automaticSources = capabilities.describe().firstOrNull { it.id == payment.provider }?.descriptor?.hasAutomaticSource ?: false,
            indeterminate = indeterminate.findByPaymentIdOrderById(paymentId).map { it.error },
            transitions =
                transitions.findByPaymentIdOrderById(paymentId).map {
                    TransitionResource(it.fromStatus?.let(PaymentStatus::fromWire), PaymentStatus.fromWire(it.toStatus)!!, it.source, it.recordedAt)
                },
            conflicts = conflicts.findByPaymentIdOrderById(paymentId).map { ConflictResource(it.source, json.readValue<Map<String, Any>>(it.reported), it.recordedAt) },
            events = recorded.map { EventResource(it.id, it.type, it.sequence, it.createdAt, byEvent[it.id].orEmpty()) },
        )
    }
}

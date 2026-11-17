package org.openfsp.gateway.payments.persistence

import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.Transition
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.spi.PaymentJournal
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

@Component
class JpaPaymentJournal(
    private val transitions: TransitionJpaRepository,
    private val conflicts: ConflictJpaRepository,
    private val attestations: AttestationJpaRepository,
    private val indeterminate: IndeterminateOperationJpaRepository,
    private val json: JsonMapper,
) : PaymentJournal {
    override fun record(transition: Transition) {
        transitions.save(TransitionJpaEntity(transition.paymentId, transition.from?.wire, transition.to.wire, transition.source.wire, transition.at))
    }

    override fun record(conflict: PaymentConflict) {
        val reported = json.writeValueAsString(mapOf("status" to conflict.reportedStatus.wire, "amount" to conflict.reportedAmount.amount))
        conflicts.save(ConflictJpaEntity(conflict.paymentId, conflict.source.wire, reported, conflict.at))
    }

    override fun record(operation: IndeterminateOperation) {
        indeterminate.save(IndeterminateOperationJpaEntity(operation.paymentId, operation.operation, operation.error, operation.at))
    }

    override fun record(
        paymentId: String,
        attestation: Attestation,
        at: Instant,
    ) {
        attestations.save(AttestationJpaEntity(paymentId, attestation.person, attestation.status.wire, attestation.failureReason?.wire, attestation.evidence, at))
    }

    override fun forget(paymentId: String) {
        transitions.deleteByPaymentId(paymentId)
        conflicts.deleteByPaymentId(paymentId)
        attestations.deleteByPaymentId(paymentId)
        indeterminate.deleteByPaymentId(paymentId)
    }

    override fun transitions(paymentId: String): List<Transition> =
        transitions.findByPaymentIdOrderById(paymentId).map {
            Transition(it.paymentId, it.fromStatus?.let(PaymentStatus::fromWire), PaymentStatus.fromWire(it.toStatus)!!, TransitionSource.fromWire(it.source), it.recordedAt)
        }

    override fun conflicts(paymentId: String): List<PaymentConflict> =
        conflicts.findByPaymentIdOrderById(paymentId).map {
            val reported = json.readTree(it.reported)
            PaymentConflict(
                it.paymentId,
                TransitionSource.fromWire(it.source),
                PaymentStatus.fromWire(reported.get("status").asString())!!,
                Money.htg(reported.get("amount").asLong()),
                it.recordedAt,
            )
        }

    override fun indeterminate(paymentId: String): List<IndeterminateOperation> =
        indeterminate.findByPaymentIdOrderById(paymentId).map { IndeterminateOperation(it.paymentId, it.operation, it.error, it.recordedAt) }
}

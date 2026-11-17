package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.Transition
import org.openfsp.gateway.payment.spi.PaymentJournal
import java.time.Instant

@Stub
class InMemoryPaymentJournal : PaymentJournal {
    private val transitions = mutableListOf<Transition>()
    private val conflicts = mutableListOf<PaymentConflict>()
    private val indeterminate = mutableListOf<IndeterminateOperation>()
    val attestations = mutableListOf<Pair<String, Attestation>>()

    override fun record(transition: Transition) {
        transitions += transition
    }

    override fun record(conflict: PaymentConflict) {
        conflicts += conflict
    }

    override fun record(operation: IndeterminateOperation) {
        indeterminate += operation
    }

    override fun record(
        paymentId: String,
        attestation: Attestation,
        at: Instant,
    ) {
        attestations += paymentId to attestation
    }

    override fun forget(paymentId: String) {
        transitions.removeIf { it.paymentId == paymentId }
        conflicts.removeIf { it.paymentId == paymentId }
        indeterminate.removeIf { it.paymentId == paymentId }
        attestations.removeIf { it.first == paymentId }
    }

    override fun transitions(paymentId: String) = transitions.filter { it.paymentId == paymentId }

    override fun conflicts(paymentId: String) = conflicts.filter { it.paymentId == paymentId }

    override fun indeterminate(paymentId: String) = indeterminate.filter { it.paymentId == paymentId }
}

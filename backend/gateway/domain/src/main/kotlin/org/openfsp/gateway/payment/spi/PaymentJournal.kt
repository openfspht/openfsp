package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.IndeterminateOperation
import org.openfsp.gateway.payment.PaymentConflict
import org.openfsp.gateway.payment.Transition
import java.time.Instant

interface PaymentJournal {
    fun record(transition: Transition)

    fun record(conflict: PaymentConflict)

    fun record(operation: IndeterminateOperation)

    fun record(
        paymentId: String,
        attestation: Attestation,
        at: Instant,
    )

    fun forget(paymentId: String)

    fun transitions(paymentId: String): List<Transition>

    fun conflicts(paymentId: String): List<PaymentConflict>

    fun indeterminate(paymentId: String): List<IndeterminateOperation>
}

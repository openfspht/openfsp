package org.openfsp.simulator.payment.spi

import org.openfsp.simulator.payment.SimulatedPayment
import java.time.Instant

interface SimulatedPayments {
    fun save(payment: SimulatedPayment): SimulatedPayment

    fun saveIfAbsent(payment: SimulatedPayment): SimulatedPayment

    fun find(paymentId: String): SimulatedPayment?

    fun withReference(
        operator: String,
        reference: String,
        account: String,
    ): SimulatedPayment?

    fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment?

    fun forget(paymentId: String)

    fun all(): List<SimulatedPayment>

    fun purge(createdBefore: Instant)

    fun clear()
}

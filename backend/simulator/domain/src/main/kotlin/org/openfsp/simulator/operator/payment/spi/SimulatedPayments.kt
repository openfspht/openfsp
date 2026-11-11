package org.openfsp.simulator.operator.payment.spi

import org.openfsp.simulator.operator.payment.SimulatedPayment

interface SimulatedPayments {
    fun save(payment: SimulatedPayment): SimulatedPayment

    fun find(paymentId: String): SimulatedPayment?

    fun withOrder(
        operator: String,
        orderId: String,
    ): SimulatedPayment?

    fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment?

    fun forget(paymentId: String)

    fun all(): List<SimulatedPayment>

    fun clear()
}

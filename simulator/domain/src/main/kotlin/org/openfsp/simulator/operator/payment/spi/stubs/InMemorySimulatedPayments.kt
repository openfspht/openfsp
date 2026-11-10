package org.openfsp.simulator.operator.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.payment.spi.SimulatedPayments
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemorySimulatedPayments : SimulatedPayments {
    private val payments = ConcurrentHashMap<String, SimulatedPayment>()

    override fun save(payment: SimulatedPayment): SimulatedPayment = payment.also { payments[it.paymentId] = it }

    override fun find(paymentId: String): SimulatedPayment? = payments[paymentId]

    override fun withOrder(
        operator: String,
        orderId: String,
    ): SimulatedPayment? = payments.values.firstOrNull { it.operator == operator && it.orderId == orderId }

    override fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment? = payments.computeIfPresent(paymentId) { _, current -> change(current) }

    override fun forget(paymentId: String) {
        payments.remove(paymentId)
    }

    override fun all(): List<SimulatedPayment> = payments.values.sortedByDescending { it.createdAt }

    override fun clear() = payments.clear()
}

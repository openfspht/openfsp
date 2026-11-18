package org.openfsp.simulator.memory

import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MemorySimulatedPayments : SimulatedPayments {
    private val held = ConcurrentHashMap<String, SimulatedPayment>()

    override fun save(payment: SimulatedPayment): SimulatedPayment = payment.also { held[it.paymentId] = it }

    override fun saveIfAbsent(payment: SimulatedPayment): SimulatedPayment =
        synchronized(held) { held.values.firstOrNull { it.operator == payment.operator && it.reference == payment.reference } ?: payment.also { held[it.paymentId] = it } }

    override fun find(paymentId: String): SimulatedPayment? = held[paymentId]

    override fun withReference(
        operator: String,
        reference: String,
    ): SimulatedPayment? = held.values.firstOrNull { it.operator == operator && it.reference == reference }

    override fun update(
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment? = held.computeIfPresent(paymentId) { _, current -> change(current) }

    override fun forget(paymentId: String) {
        held.remove(paymentId)
    }

    override fun all(): List<SimulatedPayment> = held.values.sortedByDescending { it.createdAt }

    override fun purge(createdBefore: Instant) {
        held.values.removeIf { it.createdAt.isBefore(createdBefore) }
    }

    override fun clear() {
        held.clear()
    }
}

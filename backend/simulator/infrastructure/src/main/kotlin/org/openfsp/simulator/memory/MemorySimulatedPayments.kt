package org.openfsp.simulator.memory

import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.payment.spi.SimulatedPayments
import org.openfsp.simulator.space.SpaceId
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MemorySimulatedPayments : SimulatedPayments {
    private val spaces = ConcurrentHashMap<SpaceId, ConcurrentHashMap<String, SimulatedPayment>>()

    private fun of(space: SpaceId) = spaces.computeIfAbsent(space) { ConcurrentHashMap() }

    override fun save(
        space: SpaceId,
        payment: SimulatedPayment,
    ): SimulatedPayment = payment.also { of(space)[it.paymentId] = it }

    override fun saveIfAbsent(
        space: SpaceId,
        payment: SimulatedPayment,
    ): SimulatedPayment {
        val held = of(space)
        return synchronized(held) { held.values.firstOrNull { it.operator == payment.operator && it.orderId == payment.orderId } ?: payment.also { held[it.paymentId] = it } }
    }

    override fun find(
        space: SpaceId,
        paymentId: String,
    ): SimulatedPayment? = spaces[space]?.get(paymentId)

    override fun withOrder(
        space: SpaceId,
        operator: String,
        orderId: String,
    ): SimulatedPayment? = spaces[space]?.values?.firstOrNull { it.operator == operator && it.orderId == orderId }

    override fun update(
        space: SpaceId,
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment? = spaces[space]?.computeIfPresent(paymentId) { _, current -> change(current) }

    override fun forget(
        space: SpaceId,
        paymentId: String,
    ) {
        spaces[space]?.remove(paymentId)
    }

    override fun all(space: SpaceId): List<SimulatedPayment> = spaces[space]?.values.orEmpty().sortedByDescending { it.createdAt }

    override fun purge(
        space: SpaceId,
        createdBefore: Instant,
    ) {
        spaces[space]?.values?.removeIf { it.createdAt.isBefore(createdBefore) }
    }

    override fun clear(space: SpaceId) {
        spaces.remove(space)
    }
}

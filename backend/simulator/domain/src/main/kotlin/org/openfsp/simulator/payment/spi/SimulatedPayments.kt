package org.openfsp.simulator.payment.spi

import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.space.SpaceId
import java.time.Instant

interface SimulatedPayments {
    fun save(
        space: SpaceId,
        payment: SimulatedPayment,
    ): SimulatedPayment

    fun saveIfAbsent(
        space: SpaceId,
        payment: SimulatedPayment,
    ): SimulatedPayment

    fun find(
        space: SpaceId,
        paymentId: String,
    ): SimulatedPayment?

    fun withReference(
        space: SpaceId,
        operator: String,
        reference: String,
    ): SimulatedPayment?

    fun update(
        space: SpaceId,
        paymentId: String,
        change: (SimulatedPayment) -> SimulatedPayment,
    ): SimulatedPayment?

    fun forget(
        space: SpaceId,
        paymentId: String,
    )

    fun all(space: SpaceId): List<SimulatedPayment>

    fun purge(
        space: SpaceId,
        createdBefore: Instant,
    )

    fun clear(space: SpaceId)
}

package org.openfsp.simulator.emission.spi

import org.openfsp.simulator.emission.EncodedEvent
import org.openfsp.simulator.payment.SimulatedPayment

fun interface EventEncoder {
    fun encode(payment: SimulatedPayment): EncodedEvent
}

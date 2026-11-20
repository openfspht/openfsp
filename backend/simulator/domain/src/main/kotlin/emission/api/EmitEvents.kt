package org.openfsp.simulator.emission.api

import org.openfsp.simulator.payment.SimulatedPayment

fun interface EmitEvents {
    fun emit(payment: SimulatedPayment)
}

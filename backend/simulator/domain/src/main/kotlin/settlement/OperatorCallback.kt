package org.openfsp.simulator.settlement

import org.openfsp.simulator.payment.SimulatedPayment

data class OperatorCallback(
    val paymentId: String,
    val reference: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val payment: SimulatedPayment,
)

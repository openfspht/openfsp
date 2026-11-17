package org.openfsp.simulator.settlement

import org.openfsp.simulator.payment.SimulatedPayment
import org.openfsp.simulator.space.SpaceId

data class OperatorCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val payment: SimulatedPayment,
    val space: SpaceId,
)

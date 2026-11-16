package org.openfsp.simulator.operator.settlement

import org.openfsp.simulator.operator.payment.SimulatedPayment
import org.openfsp.simulator.operator.space.SpaceId

data class OperatorCallback(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val payment: SimulatedPayment,
    val space: SpaceId,
)

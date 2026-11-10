package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.OperatorCallback
import org.openfsp.simulator.operator.OperatorPayment
import org.springframework.stereotype.Component

@Component
class OpenFspMapper {
    fun toDomain(request: OpenFspPaymentRequest) = CreatePaymentAtOperator(request.orderId, request.amount, request.msisdn, request.description, request.callbackUrl)

    fun toWire(payment: OperatorPayment) =
        OpenFspPayment(
            paymentId = payment.paymentId,
            orderId = payment.orderId,
            status = payment.status,
            amount = payment.amount,
            reason = payment.reason,
            approval = if (payment.checkoutUrl == null) PAYER_APPROVAL else REDIRECT,
            expiresIn = payment.expiresIn,
            checkoutUrl = payment.checkoutUrl,
        )

    fun toWire(callback: OperatorCallback) = OpenFspCallback(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason)

    private companion object {
        const val PAYER_APPROVAL = "PAYER_APPROVAL"
        const val REDIRECT = "REDIRECT"
    }
}

package org.openfsp.simulator.dialects.natcash

import org.openfsp.simulator.operator.CreatePaymentAtOperator
import org.openfsp.simulator.operator.OperatorPayment
import org.springframework.stereotype.Component
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

@Component
class NatCashMapper {
    fun toDomain(request: NatCashPaymentRequest) =
        CreatePaymentAtOperator(request.orderNumber, request.amount.toPlainString(), request.msisdn, null, request.callbackUrl)

    fun toCreated(payment: OperatorPayment) =
        NatCashPaymentResponse(SUCCESS, MSG_SUCCESS, SIKSE, url = payment.checkoutUrl, expiredAt = payment.expiresIn)

    fun toTransaction(payment: OperatorPayment) =
        NatCashTransactionResponse(
            SUCCESS,
            MSG_SUCCESS,
            SIKSE,
            NatCashTransaction(GROUPED.format(payment.amount.toBigDecimal()), payment.orderId, responseCodeOf(payment.status), payment.paymentId),
        )

    fun responseCodeOf(status: String): String =
        when (status) {
            "SUCCESSFUL" -> PAID
            "FAILED", "EXPIRED" -> DECLINED
            else -> PENDING
        }

    fun refused(
        code: String,
        message: String,
    ) = NatCashPaymentResponse(REFUSED, code, message)

    fun notFound() = NatCashTransactionResponse(REFUSED, "ERR_TRANSACTION_NOT_FOUND", "Transaction not found")

    fun queryRefused(message: String) = NatCashTransactionResponse(REFUSED, "ERR_UNAUTHORIZED", message)

    private companion object {
        const val SUCCESS = 0
        const val REFUSED = 101
        const val MSG_SUCCESS = "MSG_SUCCESS"
        const val SIKSE = "Sikse"
        const val PAID = "1"
        const val DECLINED = "-1"
        const val PENDING = "0"
        val GROUPED = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    }
}

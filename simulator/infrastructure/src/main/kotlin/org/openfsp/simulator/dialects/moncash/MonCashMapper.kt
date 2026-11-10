package org.openfsp.simulator.dialects.moncash

import org.openfsp.simulator.operator.OperatorResponse
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.payment.CreatePaymentAtOperator
import org.openfsp.simulator.operator.payment.OperatorPayment
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.format.DateTimeFormatter

@Component
class MonCashMapper(
    private val clock: SimulationClock,
) {
    fun toDomain(request: MonCashCreatePayment) = CreatePaymentAtOperator(request.orderId, request.amount.toPlainString(), null, null, null)

    fun toCreated(payment: OperatorPayment): MonCashCreatedPayment {
        val now = clock.localNow()
        val expires = now.plusSeconds(payment.expiresIn ?: 0)
        return MonCashCreatedPayment(CREATE_PATH, MonCashPaymentToken(STAMP.format(expires), STAMP.format(now), payment.paymentId), ACCEPTED)
    }

    fun toResult(
        path: String,
        payment: OperatorPayment,
    ): MonCashPaymentResult? {
        val message = MESSAGES[payment.status] ?: return null
        return MonCashPaymentResult(path, MonCashPayment(payment.orderId, payment.paymentId, BigDecimal(payment.amount), message, null), OK)
    }

    fun toError(
        path: String,
        refusal: OperatorResponse.Refused,
    ) = MonCashError(path, refusal.code, refusal.message, refusal.status)

    fun notFound(path: String) = MonCashError(path, "Not Found", "Can't find this transaction", NOT_FOUND)

    companion object {
        const val CREATE_PATH = "/v1/CreatePayment"
        private const val OK = 200
        private const val ACCEPTED = 202
        private const val NOT_FOUND = 404
        private val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")
        private val MESSAGES = mapOf("SUCCESSFUL" to "successful", "FAILED" to "failed", "EXPIRED" to "failed", "IN_REVIEW" to "in review")
    }
}

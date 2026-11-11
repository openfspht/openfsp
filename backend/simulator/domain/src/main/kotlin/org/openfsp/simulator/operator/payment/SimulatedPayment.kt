package org.openfsp.simulator.operator.payment

import org.openfsp.simulator.operator.scenario.Scenario
import java.time.Duration
import java.time.Instant

enum class Outcome(
    val wire: String,
) {
    SUCCESSFUL("SUCCESSFUL"),
    FAILED("FAILED"),
    EXPIRED("EXPIRED"),
}

data class SimulatedPayment(
    val operator: String,
    val paymentId: String,
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val callbackUrl: String?,
    val scenario: Scenario,
    val createdAt: Instant,
    val expiresAt: Instant,
    val outcome: Outcome? = null,
    val reason: String? = null,
    val reportedAmount: String? = null,
    val callbacksSent: Int = 0,
    val lookups: Int = 0,
    val description: String? = null,
    val locale: String? = null,
    val returnUrl: String? = null,
    val settledAt: Instant? = null,
) {
    val isSettled: Boolean get() = outcome != null

    fun settle(
        outcome: Outcome,
        reason: String?,
        at: Instant,
    ): SimulatedPayment =
        if (isSettled) {
            this
        } else {
            copy(
                outcome = outcome,
                reason = reason,
                settledAt = at,
                reportedAmount = if (scenario == Scenario.AMOUNT_MISMATCH) amount.toBigDecimal().minus(java.math.BigDecimal.ONE).toPlainString() else null,
            )
        }

    fun asReported(): OperatorPayment = OperatorPayment(paymentId, orderId, outcome?.wire ?: PENDING, reportedAmount ?: amount, reason, expiresIn())

    fun asLookedUp(): OperatorPayment =
        when {
            scenario == Scenario.UNKNOWN_STATUS && isSettled -> asReported().copy(status = "IN_REVIEW")
            scenario == Scenario.CONTRADICT && outcome == Outcome.SUCCESSFUL && lookups > 1 -> asReported().copy(status = "FAILED", reason = "DECLINED")
            else -> asReported()
        }

    private fun expiresIn(): Long? = if (isSettled) null else Duration.between(createdAt, expiresAt).seconds

    private companion object {
        const val PENDING = "PENDING"
    }
}

data class OperatorPayment(
    val paymentId: String,
    val orderId: String,
    val status: String,
    val amount: String,
    val reason: String?,
    val expiresIn: Long?,
    val checkoutUrl: String? = null,
)

data class CreatePaymentAtOperator(
    val orderId: String,
    val amount: String,
    val msisdn: String?,
    val description: String?,
    val callbackUrl: String?,
    val locale: String? = null,
    val returnUrl: String? = null,
)

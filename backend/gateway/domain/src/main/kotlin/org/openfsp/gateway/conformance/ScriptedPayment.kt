package org.openfsp.gateway.conformance

import org.openfsp.gateway.provider.FeeBearer
import org.openfsp.gateway.provider.ProviderFee
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.time.Instant

data class ScriptedOrder(
    val provider: String,
    val orderId: String,
)

data class ScriptedPayment(
    val run: String,
    val order: ScriptedOrder,
    val scenario: Scenario,
    val amount: Money,
    val providerReference: String,
    val secret: String,
    val callbackToken: String?,
    val notifies: Boolean,
    val hasLookup: Boolean,
    val reportsFee: Boolean,
    val nextAction: NextAction,
    val expiresAt: Instant?,
    val accepted: Boolean = true,
    val outcome: ScriptedOutcome? = null,
    val callbacksSent: Int = 0,
) {
    val isSettled: Boolean get() = outcome != null

    val isObservable: Boolean get() = notifies || hasLookup

    fun settle(outcome: ScriptedOutcome): ScriptedPayment = if (isSettled || !accepted) this else copy(outcome = outcome)

    fun isDueToExpireAt(instant: Instant): Boolean = scenario == Scenario.EXPIRE && !isSettled && expiresAt != null && !instant.isBefore(expiresAt)

    fun presented(): ProviderPayment = ProviderPayment(report(), if (isSettled) NextAction(NextAction.Type.NONE) else nextAction, expiresAt)

    fun report(): ProviderReport {
        val status = outcome?.status ?: PaymentStatus.PENDING
        return ProviderReport(
            orderId = order.orderId,
            providerReference = providerReference,
            status = status,
            amount = amount,
            failureReason = outcome?.failureReason,
            failureDetail = outcome?.failureReason?.let { mapOf(CODE to it.name, MESSAGE to (MESSAGES[it] ?: DEFAULT_MESSAGE)) },
            fee = fee().takeIf { status == PaymentStatus.SUCCEEDED && reportsFee },
        )
    }

    fun lookedUp(): ProviderReport =
        if (scenario == Scenario.UNKNOWN_STATUS && isSettled) ProviderReport(order.orderId, providerReference, PaymentStatus.PENDING, amount = null) else report()

    private fun fee() = ProviderFee(Money.htg((amount.amount * FEE_BASIS_POINTS + BASIS_POINTS / 2) / BASIS_POINTS), FeeBearer.MERCHANT)

    private companion object {
        const val CODE = "code"
        const val MESSAGE = "message"
        const val DEFAULT_MESSAGE = "The payment failed"
        val MESSAGES =
            mapOf(
                FailureReason.DECLINED to "The payment was declined",
                FailureReason.INSUFFICIENT_FUNDS to "The payer balance is insufficient",
                FailureReason.PAYER_CANCELED to "The payer canceled the payment",
                FailureReason.LIMIT_EXCEEDED to "The payer limit is exceeded",
            )
        const val FEE_BASIS_POINTS = 200L
        const val BASIS_POINTS = 10_000L
    }
}

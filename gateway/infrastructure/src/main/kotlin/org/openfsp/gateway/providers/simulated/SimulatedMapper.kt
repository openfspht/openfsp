package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.time.LocalDateTime

class SimulatedMapper(
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) {
    fun toPaymentRequest(request: ProviderPaymentRequest) =
        SimulatedPaymentRequest(
            orderId = request.orderId,
            amount = amounts.toDecimal(request.amount),
            msisdn = request.payerPhoneNumber?.removePrefix("+"),
            description = request.description,
            callbackUrl = request.callbackUrl.toString(),
        )

    fun toPayment(payment: SimulatedOperatorPayment): ProviderPayment {
        val expiresAt = payment.expiresIn?.let { calendar.now().plusSeconds(it) }
        val nextAction =
            when (payment.approval) {
                PAYER_APPROVAL -> NextAction.payerApproval
                REDIRECT -> NextAction.redirect(payment.checkoutUrl ?: throw ProviderFailure.Rejected("Redirect without checkout URL from the provider"), expiresAt)
                else -> throw ProviderFailure.Rejected("Unknown approval type from the provider")
            }
        return ProviderPayment(toReport(payment), nextAction, expiresAt)
    }

    fun toReport(payment: SimulatedOperatorPayment): ProviderReport = report(payment.paymentId, payment.orderId, payment.status, payment.amount, payment.reason, payment.message)

    fun toReport(callback: SimulatedCallback): ProviderReport = report(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason, callback.message)

    fun toStatement(statement: SimulatedStatement) =
        ProviderStatement(
            businessDate = statement.businessDate,
            closed = statement.closed,
            lines =
                statement.entries.map { entry ->
                    ProviderStatementLine(
                        providerReference = entry.reference,
                        orderId = entry.orderId,
                        type = entry.type.lowercase().takeIf { it in LINE_TYPES } ?: ADJUSTMENT,
                        amount = amounts.fromDecimal(entry.amount),
                        bookedAt = calendar.fromLocal(LocalDateTime.parse(entry.bookedAt)),
                    )
                },
        )

    private fun report(
        paymentId: String,
        orderId: String,
        status: String,
        amount: String,
        reason: String?,
        message: String?,
    ): ProviderReport {
        val mapped = STATUSES[status] ?: throw ProviderFailure.Rejected("Unknown payment status from the provider")
        val failed = mapped == PaymentStatus.FAILED
        return ProviderReport(
            orderId = orderId,
            providerReference = paymentId,
            status = mapped,
            amount = amounts.fromDecimal(amount),
            failureReason = (REASONS[reason] ?: FailureReason.UNSPECIFIED).takeIf { failed },
            failureDetail = mapOf("code" to reason, "message" to message).filterValues { it != null }.takeIf { failed && it.isNotEmpty() },
        )
    }

    private companion object {
        const val PAYER_APPROVAL = "PAYER_APPROVAL"
        const val REDIRECT = "REDIRECT"
        const val ADJUSTMENT = "adjustment"
        val LINE_TYPES = setOf("payment", "reversal", "fee", ADJUSTMENT)
        val STATUSES =
            mapOf(
                "PENDING" to PaymentStatus.PENDING,
                "SUCCESSFUL" to PaymentStatus.SUCCEEDED,
                "FAILED" to PaymentStatus.FAILED,
                "EXPIRED" to PaymentStatus.EXPIRED,
            )
        val REASONS =
            mapOf(
                "DECLINED" to FailureReason.DECLINED,
                "INSUFFICIENT_FUNDS" to FailureReason.INSUFFICIENT_FUNDS,
                "PAYER_CANCELED" to FailureReason.PAYER_CANCELED,
            )
    }
}

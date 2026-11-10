package org.openfsp.gateway.provider

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Instant
import java.time.LocalDate

data class ProviderPaymentRequest(
    val orderId: String,
    val amount: Money,
    val payerPhoneNumber: String?,
    val description: String?,
    val callbackUrl: URI,
)

data class ProviderReport(
    val orderId: String,
    val providerReference: String?,
    val status: PaymentStatus,
    val amount: Money,
    val failureReason: FailureReason? = null,
    val failureDetail: Map<String, Any?>? = null,
)

data class ProviderPayment(
    val report: ProviderReport,
    val nextAction: NextAction,
    val expiresAt: Instant?,
)

data class ProviderStatementLine(
    val providerReference: String,
    val orderId: String?,
    val type: String,
    val amount: Money,
    val feeAmount: Money? = null,
    val netAmount: Money? = null,
    val bookedAt: Instant,
)

data class ProviderStatement(
    val businessDate: LocalDate,
    val closed: Boolean,
    val lines: List<ProviderStatementLine>,
)

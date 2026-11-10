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
    val locale: String? = null,
)

data class ProviderReport(
    val orderId: String,
    val providerReference: String?,
    val status: PaymentStatus,
    val amount: Money?,
    val failureReason: FailureReason? = null,
    val failureDetail: Map<String, Any?>? = null,
) {
    init {
        if (providerReference != null && providerReference.codePointCount(0, providerReference.length) > MAX_PROVIDER_REFERENCE) {
            throw ProviderFailure.Rejected("The provider reference exceeds $MAX_PROVIDER_REFERENCE characters")
        }
    }

    private companion object {
        const val MAX_PROVIDER_REFERENCE = 255
    }
}

data class ProviderPayment(
    val report: ProviderReport,
    val nextAction: NextAction,
    val expiresAt: Instant?,
)

data class CallbackRequest(
    val headers: (String) -> String?,
    val parameters: Map<String, String>,
    val body: ByteArray,
) {
    override fun equals(other: Any?): Boolean = this === other

    override fun hashCode(): Int = System.identityHashCode(this)
}

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

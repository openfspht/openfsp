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

enum class Source(
    val wire: String,
) {
    SIGNED_CALLBACK("signed_callback"),
    LOOKUP("lookup"),
    CREATION("creation"),
    ATTESTATION("attestation"),
    PER_PAYMENT_URL("per_payment_url"),
    STATEMENT("statement"),
}

data class ProviderDescriptor(
    val displayName: String,
    val capabilities: Set<String>,
    val nextActions: Set<NextAction.Type>,
    val payerRequired: Boolean,
    val returnUrlRequired: Boolean = false,
    val expiryGuaranteed: Boolean = false,
    val locales: Set<String> = emptySet(),
    val idempotentCreation: Boolean = true,
) {
    init {
        require(PAYMENTS in capabilities) { "every provider announces payments" }
    }

    val hasLookup: Boolean get() = LOOKUP in capabilities

    val hasPerPaymentUrl: Boolean get() = PER_PAYMENT_URL in capabilities

    val hasStatement: Boolean get() = STATEMENT in capabilities

    companion object {
        const val PAYMENTS = "payments"
        const val LOOKUP = "payments.lookup"
        const val WEBHOOKS_VERIFY = "webhooks.verify"
        const val WEBHOOKS_EMIT = "webhooks.emit"
        const val PER_PAYMENT_URL = "webhooks.per_payment_url"
        const val STATEMENT = "payments.statement"
    }
}

interface ProviderAdapter {
    val id: String
    val descriptor: ProviderDescriptor

    fun create(request: ProviderPaymentRequest): ProviderPayment

    fun lookup(orderId: String): ProviderReport

    fun verifiedCallback(
        headers: (String) -> String?,
        body: ByteArray,
    ): ProviderReport?

    fun unsignedCallback(body: ByteArray): ProviderReport?

    fun statement(day: LocalDate): ProviderStatement
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

sealed class ProviderException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    class Unavailable(
        cause: Throwable,
    ) : ProviderException("The provider is unreachable", cause)

    class Timeout(
        cause: Throwable,
    ) : ProviderException("The provider did not respond in time", cause)

    class Error(
        message: String,
        val detail: Map<String, Any?>? = null,
    ) : ProviderException(message)
}

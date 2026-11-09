package org.openfsp.gateway.provider

import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Instant

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
}

interface ProviderAdapter {
    val id: String
    val requiresPayer: Boolean

    fun create(request: ProviderPaymentRequest): ProviderPayment

    fun lookup(orderId: String): ProviderReport

    fun verifiedCallback(
        headers: (String) -> String?,
        body: ByteArray,
    ): ProviderReport?
}

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

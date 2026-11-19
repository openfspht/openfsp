package org.openfsp.sdk.model

import org.openfsp.protocol.Locale
import java.time.Instant

data class PaymentRequest(
    val reference: String,
    val amount: Money,
    val provider: String,
    val payerPhoneNumber: String? = null,
    val description: String? = null,
    val returnUrl: String? = null,
    val callbackUrl: String? = null,
    val expiresAt: Instant? = null,
    val locale: Locale? = null,
    val metadata: Map<String, String> = emptyMap(),
) {
    class Builder internal constructor(
        private var request: PaymentRequest,
    ) {
        fun payerPhoneNumber(value: String) = apply { request = request.copy(payerPhoneNumber = value) }

        fun description(value: String) = apply { request = request.copy(description = value) }

        fun returnUrl(value: String) = apply { request = request.copy(returnUrl = value) }

        fun callbackUrl(value: String) = apply { request = request.copy(callbackUrl = value) }

        fun expiresAt(value: Instant) = apply { request = request.copy(expiresAt = value) }

        fun locale(value: Locale) = apply { request = request.copy(locale = value) }

        fun metadata(value: Map<String, String>) = apply { request = request.copy(metadata = value.toMap()) }

        fun build(): PaymentRequest = request
    }

    companion object {
        @JvmStatic
        fun builder(
            reference: String,
            amount: Money,
            provider: String,
        ): Builder = Builder(PaymentRequest(reference, amount, provider))
    }
}

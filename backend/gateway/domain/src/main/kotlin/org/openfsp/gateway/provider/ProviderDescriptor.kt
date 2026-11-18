package org.openfsp.gateway.provider

import org.openfsp.protocol.NextAction
import java.net.URI

data class ProviderDescriptor(
    val displayName: String,
    val capabilities: Set<String>,
    val nextActions: Set<NextAction.Type>,
    val payerRequired: Boolean,
    val returnUrlRequired: Boolean = false,
    val expiryGuaranteed: Boolean = false,
    val locales: Set<String> = emptySet(),
    val idempotentCreation: Boolean = true,
    val logoUrl: URI? = null,
    val signedCallbacks: Boolean = false,
    val paymentUrl: Boolean = false,
) {
    init {
        require(PAYMENTS in capabilities) { "every provider announces payments" }
        require(capabilities.all { it in PROVIDER_CAPABILITIES }) { "a provider announces only ${PROVIDER_CAPABILITIES.joinToString()}" }
    }

    val hasLookup: Boolean get() = LOOKUP in capabilities

    val hasAutomaticSource: Boolean get() = hasLookup || signedCallbacks || paymentUrl

    companion object {
        const val PAYMENTS = "payments"
        const val LOOKUP = "payments.lookup"
        const val FEE = "payments.fee"
        const val STATEMENT = "payments.statement"
        const val WEBHOOKS_EMIT = "webhooks.emit"

        val PROVIDER_CAPABILITIES = setOf(PAYMENTS, LOOKUP, FEE)
        val SUPPORTED = (PROVIDER_CAPABILITIES + STATEMENT + WEBHOOKS_EMIT).sorted()
    }
}

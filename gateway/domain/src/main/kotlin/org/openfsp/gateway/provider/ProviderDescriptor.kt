package org.openfsp.gateway.provider

import org.openfsp.protocol.NextAction

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

    val hasSignedCallbacks: Boolean get() = WEBHOOKS_VERIFY in capabilities

    val hasPaymentUrl: Boolean get() = PAYMENT_URL in capabilities

    val hasStatement: Boolean get() = STATEMENT in capabilities

    val hasAutomaticSource: Boolean get() = hasLookup || hasSignedCallbacks || hasPaymentUrl || hasStatement

    val staticNextAction: NextAction? get() = nextActions.singleOrNull()?.takeIf { it != NextAction.Type.REDIRECT }?.let(::NextAction)

    companion object {
        const val PAYMENTS = "payments"
        const val LOOKUP = "payments.lookup"
        const val STATEMENT = "payments.statement"
        const val WEBHOOKS_VERIFY = "webhooks.verify"
        const val WEBHOOKS_EMIT = "webhooks.emit"
        const val PAYMENT_URL = "webhooks.per_payment_url"
    }
}

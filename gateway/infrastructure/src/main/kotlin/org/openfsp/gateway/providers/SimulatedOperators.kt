package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.protocol.NextAction

object SimulatedOperators {
    val descriptors: Map<String, ProviderDescriptor> =
        mapOf(
            "mock_alpha" to
                ProviderDescriptor(
                    displayName = "Mock Alpha",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.WEBHOOKS_VERIFY),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = true,
                ),
            "mock_beta" to
                ProviderDescriptor(
                    displayName = "Mock Beta",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS),
                    nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
                    payerRequired = true,
                    idempotentCreation = false,
                ),
            "mock_epsilon" to
                ProviderDescriptor(
                    displayName = "Mock Epsilon",
                    capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.PAYMENT_URL, ProviderDescriptor.STATEMENT),
                    nextActions = setOf(NextAction.Type.REDIRECT),
                    payerRequired = false,
                    returnUrlRequired = true,
                    idempotentCreation = false,
                ),
        )
}

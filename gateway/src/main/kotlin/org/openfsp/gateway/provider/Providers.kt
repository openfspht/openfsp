package org.openfsp.gateway.provider

import org.openfsp.gateway.GatewayProperties
import org.openfsp.protocol.NextAction
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Clock

@Component
class Providers(
    properties: GatewayProperties,
    json: JsonMapper,
    clock: Clock,
) {
    private val adapters: Map<String, ProviderAdapter> =
        properties.providers.mapValues { (id, settings) ->
            val descriptor = SIMULATED[id] ?: error("No adapter for provider $id")
            SimulatedOperatorAdapter(id, descriptor, settings, properties.providerTimeout, json.lenient(), clock)
        }

    operator fun get(id: String): ProviderAdapter? = adapters[id]

    val ids: Set<String> get() = adapters.keys
}

private val SIMULATED =
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
            ),
    )

private fun JsonMapper.lenient(): JsonMapper =
    rebuild().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

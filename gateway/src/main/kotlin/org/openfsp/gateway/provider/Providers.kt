package org.openfsp.gateway.provider

import org.openfsp.gateway.GatewayProperties
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
            when (id) {
                AlphaAdapter.ID -> AlphaAdapter(settings, properties.providerTimeout, json.lenient(), clock)
                else -> error("No adapter for provider $id")
            }
        }

    operator fun get(id: String): ProviderAdapter? = adapters[id]

    val ids: Set<String> get() = adapters.keys
}

private fun JsonMapper.lenient(): JsonMapper =
    rebuild().disable(tools.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build()

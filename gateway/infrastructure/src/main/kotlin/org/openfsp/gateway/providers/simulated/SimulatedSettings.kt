package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.providers.ProviderProperties
import java.net.URI

data class SimulatedSettings(
    val baseUrl: URI,
    val apiKey: String,
    val callbackSecret: String?,
) {
    val secrets: List<String?> get() = listOf(apiKey, callbackSecret)

    companion object {
        fun from(
            id: String,
            provider: ProviderProperties.Provider,
        ) = SimulatedSettings(
            baseUrl = provider.baseUrl,
            apiKey = provider.apiKey ?: error("$id needs an api-key"),
            callbackSecret = provider.callbackSecret,
        )
    }
}

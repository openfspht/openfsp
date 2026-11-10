package org.openfsp.gateway.providers

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("openfsp")
data class ProviderProperties(
    val providerTimeout: Duration = Duration.ofSeconds(10),
    val providers: Map<String, Provider> = emptyMap(),
) {
    data class Provider(
        val type: String = SIMULATED,
        val baseUrl: URI,
        val secondaryUrl: URI? = null,
        val apiKey: String? = null,
        val callbackSecret: String? = null,
        val credentials: Map<String, String> = emptyMap(),
        val limits: Limits = Limits(),
    ) {
        fun credential(name: String): String = credentials[name] ?: error("credential $name is missing for this provider")

        companion object {
            const val SIMULATED = "simulated"
        }
    }

    data class Limits(
        val minAmount: Long? = null,
        val maxAmount: Long? = null,
        val amountStep: Long? = null,
    )
}

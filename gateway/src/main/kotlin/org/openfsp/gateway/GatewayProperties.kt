package org.openfsp.gateway

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration
import java.util.Base64

@ConfigurationProperties("openfsp")
data class GatewayProperties(
    val environment: Environment,
    val developmentMode: Boolean,
    val publicBaseUrl: URI,
    val orderIdDerivationKey: String,
    val providerTimeout: Duration,
    val providers: Map<String, ProviderSettings>,
) {
    val derivationKeyBytes: ByteArray get() = Base64.getDecoder().decode(orderIdDerivationKey)

    enum class Environment { TEST, LIVE }

    data class ProviderSettings(
        val baseUrl: URI,
        val apiKey: String,
        val callbackSecret: String,
    )
}

package org.openfsp.gateway.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.util.Base64

@ConfigurationProperties("openfsp")
data class GatewayProperties(
    val environment: Environment,
    val developmentMode: Boolean,
    val publicBaseUrl: URI,
    val orderIdDerivationKey: String,
    val adminToken: String? = null,
    val bootstrapKey: String? = null,
    val tlsTerminatedByProxy: Boolean = false,
) {
    val derivationKeyBytes: ByteArray get() = Base64.getDecoder().decode(orderIdDerivationKey)

    val environmentName: String get() = environment.name.lowercase()

    enum class Environment { TEST, LIVE }
}

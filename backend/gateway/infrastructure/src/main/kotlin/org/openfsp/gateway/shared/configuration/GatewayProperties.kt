package org.openfsp.gateway.shared.configuration

import org.openfsp.gateway.common.BusinessCalendar
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration
import java.time.ZoneId
import java.util.Base64

@ConfigurationProperties("openfsp")
data class GatewayProperties(
    val environment: Environment,
    val developmentMode: Boolean,
    val publicBaseUrl: URI,
    val orderIdDerivationKey: String,
    val adminToken: String? = null,
    val adminSessionLifetime: Duration = Duration.ofMinutes(20),
    val bootstrapKey: String? = null,
    val credentialsKey: String? = null,
    val tlsTerminatedByProxy: Boolean = false,
    val timeZone: ZoneId = BusinessCalendar.HAITI,
) {
    val derivationKeyBytes: ByteArray get() = Base64.getDecoder().decode(orderIdDerivationKey)

    val environmentName: String get() = environment.name.lowercase()

    enum class Environment { TEST, LIVE }
}

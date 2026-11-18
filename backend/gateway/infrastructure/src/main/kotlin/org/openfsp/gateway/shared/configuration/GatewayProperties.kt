package org.openfsp.gateway.shared.configuration

import org.openfsp.gateway.common.BusinessCalendar
import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration
import java.time.ZoneId

@ConfigurationProperties("openfsp")
data class GatewayProperties(
    val environment: Environment,
    val developmentMode: Boolean,
    val publicBaseUrl: URI,
    val appKey: String,
    val adminToken: String? = null,
    val adminSessionLifetime: Duration = Duration.ofMinutes(20),
    val adminSignInConcurrency: Int = 2,
    val bootstrapKey: String? = null,
    val tlsTerminatedByProxy: Boolean = false,
    val trustedProxies: List<String> = emptyList(),
    val timeZone: ZoneId = BusinessCalendar.HAITI,
) {
    val environmentName: String get() = environment.name.lowercase()

    enum class Environment { TEST, LIVE }
}

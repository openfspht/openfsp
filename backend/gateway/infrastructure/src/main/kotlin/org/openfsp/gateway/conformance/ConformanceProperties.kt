package org.openfsp.gateway.conformance

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.Duration

@ConfigurationProperties("openfsp.conformance")
data class ConformanceProperties(
    val enabled: Boolean = false,
    val accessCode: String? = null,
    val lifetime: Duration = Duration.ofHours(1),
    val maxActive: Int = 8,
)

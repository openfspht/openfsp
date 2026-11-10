package org.openfsp.gateway.providers

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("openfsp")
data class ProviderProperties(
    val providerTimeout: Duration,
    val providers: Map<String, Provider>,
) {
    data class Provider(
        val baseUrl: URI,
        val apiKey: String,
        val callbackSecret: String? = null,
        val limits: Limits = Limits(),
    )

    data class Limits(
        val minAmount: Long? = null,
        val maxAmount: Long? = null,
        val amountStep: Long? = null,
    )
}

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
    val webhooks: Webhooks = Webhooks(),
) {
    val derivationKeyBytes: ByteArray get() = Base64.getDecoder().decode(orderIdDerivationKey)

    enum class Environment { TEST, LIVE }

    data class Webhooks(
        val endpoints: Map<String, Endpoint> = emptyMap(),
    ) {
        data class Endpoint(
            val url: URI,
            val events: Set<String>,
            val enabled: Boolean = true,
        )
    }

    data class ProviderSettings(
        val baseUrl: URI,
        val apiKey: String,
        val callbackSecret: String,
        val limits: Limits = Limits(),
    )

    data class Limits(
        val minAmount: Long? = null,
        val maxAmount: Long? = null,
        val amountStep: Long? = null,
    ) {
        init {
            require(minAmount == null || maxAmount == null || minAmount <= maxAmount) { "min-amount must not exceed max-amount" }
            require(amountStep == null || amountStep > 0) { "amount-step must be positive" }
        }
    }
}

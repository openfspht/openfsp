package org.openfsp.gateway.providers

import org.springframework.boot.context.properties.ConfigurationProperties
import org.openfsp.protocol.NextAction
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
        val logoUrl: String? = null,
        val profile: SimulatedProfile? = null,
    ) {
        fun credential(name: String): String =
            credentials.entries.firstOrNull { normalized(it.key) == normalized(name) }?.value ?: error("credential $name is missing for this provider")

        companion object {
            const val SIMULATED = "simulated"

            private fun normalized(name: String) = name.lowercase().filter { it.isLetterOrDigit() }
        }
    }

    data class SimulatedProfile(
        val displayName: String,
        val capabilities: Set<String>,
        val nextActions: Set<NextAction.Type>,
        val payerRequired: Boolean = false,
        val returnUrlRequired: Boolean = false,
        val idempotentCreation: Boolean = true,
        val locales: Set<String> = emptySet(),
    )

    data class Limits(
        val minAmount: Long? = null,
        val maxAmount: Long? = null,
        val amountStep: Long? = null,
    )
}

package org.openfsp.sdk

import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.sdk.http.Api
import org.openfsp.sdk.http.Clock
import org.openfsp.sdk.http.JdkTransport
import org.openfsp.sdk.http.Transport
import org.openfsp.sdk.model.Provider
import org.openfsp.sdk.model.ServerConfiguration
import org.openfsp.sdk.protocol.Mapping
import org.openfsp.sdk.protocol.WireConfiguration
import org.openfsp.sdk.webhooks.Webhooks
import java.time.Duration
import java.time.Instant

class OpenFsp
    @JvmOverloads
    constructor(
        val config: OpenFspConfig,
        transport: Transport = JdkTransport(),
        private val clock: Clock = Clock.SYSTEM,
        nonceStore: NonceStore? = null,
    ) {
        private val api = Api(config, transport, clock)

        @Volatile
        private var cachedProviders: CachedProviders? = null

        @get:JvmName("payments")
        val payments: Payments = Payments(api)

        @get:JvmName("webhooks")
        val webhooks: Webhooks = Webhooks({ configuration().webhookKeys }, config.webhookUrl, nonceStore, clock)

        fun providers(): List<Provider> {
            val now = clock.now()
            cachedProviders?.takeIf { now.isBefore(it.fetchedAt.plus(PROVIDERS_TTL)) }?.let { return it.providers }
            val providers = Mapping.toProviders(api.get("v1/capabilities", CapabilitiesResource::class.java))
            cachedProviders = CachedProviders(providers, now)
            return providers
        }

        fun provider(id: String): Provider? = providers().firstOrNull { it.id == id }

        fun configuration(): ServerConfiguration = Mapping.toConfiguration(api.get(".well-known/openfsp-configuration", WireConfiguration::class.java, authenticated = false))

        override fun toString(): String = "OpenFsp(${config.baseUrl})"

        private class CachedProviders(
            val providers: List<Provider>,
            val fetchedAt: Instant,
        )

        companion object {
            private val PROVIDERS_TTL: Duration = Duration.ofSeconds(60)

            @JvmStatic
            @JvmOverloads
            fun fromEnv(environment: Map<String, String> = System.getenv()): OpenFsp = OpenFsp(OpenFspConfig.fromEnv(environment))
        }
    }

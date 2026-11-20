package org.openfsp.sdk

import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.sdk.http.Api
import org.openfsp.sdk.http.JdkTransport
import org.openfsp.sdk.http.OpenFspClock
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
        private val clock: OpenFspClock = OpenFspClock.SYSTEM,
        nonceStore: NonceStore? = null,
    ) {
        private val api = Api(config, transport, clock)

        @Volatile
        private var cachedProviders: CachedProviders? = null

        @get:JvmName("payments")
        val payments: Payments = Payments(api, config.recoverLostAnswers)

        @get:JvmName("webhooks")
        val webhooks: Webhooks = Webhooks.loading({ configuration().webhookKeys }, config.webhookUrl, nonceStore, clock)

        @get:JvmName("statements")
        val statements: Statements = Statements(api)

        fun providers(): List<Provider> {
            val now = clock.now()
            cachedProviders?.takeIf { now.isBefore(it.freshUntil) }?.let { return it.providers }
            val fetched = api.fetch("v1/capabilities", CapabilitiesResource::class.java)
            val providers = Mapping.toProviders(fetched.value)
            cachedProviders = CachedProviders(providers, now.plus(fetched.maxAge?.coerceAtMost(PROVIDERS_MAX_TTL) ?: PROVIDERS_TTL))
            return providers
        }

        fun provider(id: String): Provider? = providers().firstOrNull { it.id == id }

        fun configuration(): ServerConfiguration = Mapping.toConfiguration(api.get(".well-known/openfsp-configuration", WireConfiguration::class.java, authenticated = false))

        override fun toString(): String = "OpenFsp(${config.baseUrl})"

        private class CachedProviders(
            val providers: List<Provider>,
            val freshUntil: Instant,
        )

        companion object {
            private val PROVIDERS_TTL: Duration = Duration.ofSeconds(60)
            private val PROVIDERS_MAX_TTL: Duration = Duration.ofHours(1)

            @JvmStatic
            @JvmOverloads
            fun fromEnv(environment: Map<String, String> = System.getenv()): OpenFsp = OpenFsp(OpenFspConfig.fromEnv(environment))
        }
    }

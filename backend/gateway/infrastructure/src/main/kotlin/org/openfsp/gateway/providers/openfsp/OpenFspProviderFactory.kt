package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.webhook.NonceStore
import org.openfsp.sdk.OpenFsp
import org.openfsp.sdk.OpenFspConfig
import org.openfsp.sdk.http.JdkTransport
import org.openfsp.sdk.http.OpenFspClock
import org.openfsp.sdk.http.RetryPolicy
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.random.Random

@Component
class OpenFspProviderFactory(
    private val properties: ProviderProperties,
    private val clock: Clock,
    private val nonces: NonceStore,
) : ProviderFactory {
    override val type = ProviderProperties.Provider.OPENFSP

    override fun create(setup: ProviderSetup): ProviderOperations {
        val apiKey = setup.settings.apiKey ?: error("${setup.id} needs an api-key")
        val timeout = properties.providerTimeout
        val config =
            OpenFspConfig(
                baseUrl = setup.settings.baseUrl.toString(),
                apiKey = apiKey,
                timeout = timeout,
                retryPolicy = RetryPolicy.NONE,
                allowPlainHttp = true,
                recoverLostAnswers = false,
            )
        val transport = JdkTransport(HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build())
        val server = OpenFsp(config, transport, GatewayClock(clock), nonces)
        val mapper = OpenFspMapper(setup.id, SecretRedaction(listOf(apiKey)))
        val capabilities = OpenFspCapabilities(setup.id, server, mapper, setup.settings.displayName, setup.logoUrl, clock)
        return OpenFspProvider(setup.id, setup.limits, server, mapper, capabilities)
    }

    private class GatewayClock(
        private val clock: Clock,
    ) : OpenFspClock {
        override fun now(): Instant = clock.instant()

        override fun sleep(duration: Duration) = Thread.sleep(duration.toMillis())

        override fun random(): Double = Random.nextDouble()
    }
}

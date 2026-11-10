package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.ProviderFactory
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.webhook.InMemoryNonceStore
import org.openfsp.protocol.webhook.WebhookVerifier
import org.springframework.stereotype.Component
import java.net.URI
import java.time.Clock

@Component
class OpenFspProviderFactory(
    private val http: ProviderHttpFactory,
    private val json: ProviderJson,
    private val clock: Clock,
) : ProviderFactory {
    override val type = ProviderProperties.Provider.OPENFSP

    override fun create(setup: ProviderSetup): ProviderOperations {
        val apiKey = setup.settings.apiKey ?: error("${setup.id} needs an api-key")
        val baseUrl = URI(setup.settings.baseUrl.toString().trimEnd('/'))
        val redaction = SecretRedaction(listOf(apiKey))
        val api = OpenFspApi(http.create(baseUrl, bearer = apiKey), http.create(baseUrl), json, redaction)
        val mapper = OpenFspMapper(setup.id, redaction)
        val capabilities = OpenFspCapabilities(setup.id, api, mapper, setup.settings.displayName, setup.logoUrl, clock)
        val verifier = WebhookVerifier(OpenFspWebhookKeys(setup.id, api, clock), InMemoryNonceStore(clock), clock)
        return OpenFspProvider(setup.id, setup.limits, api, mapper, capabilities, verifier, json)
    }
}

package org.openfsp.gateway.discovery

import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.conformance.ConformanceController
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.webhook.SignatureParams
import org.openfsp.protocol.webhook.WebhookJwk
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ServiceConfigurationMapper {
    fun toResource(
        publicBaseUrl: URI,
        capabilities: List<String>,
        webhookKeys: List<WebhookJwk>,
        preparesConformance: Boolean,
    ): ConfigurationResource {
        val issuer = publicBaseUrl.toString().trimEnd('/')
        val api = "$issuer/v1"
        return ConfigurationResource(
            issuer = issuer,
            protocolVersion = ProtocolVersion.CURRENT,
            apiBase = api,
            paymentsEndpoint = "$api/payments",
            capabilitiesEndpoint = "$api/capabilities",
            statementsEndpoint = "$api/statements",
            webhookKeys = webhookKeys,
            authenticationMethodsSupported = listOf(BEARER),
            scopesSupported = Scope.entries.map { it.wire },
            capabilitiesSupported = capabilities,
            webhookSignatureAlgorithmsSupported = listOf(SignatureParams.ED25519),
            conformanceEndpoint = "$issuer${ConformanceController.PATH}".takeIf { preparesConformance },
        )
    }

    private companion object {
        const val BEARER = "bearer"
    }
}

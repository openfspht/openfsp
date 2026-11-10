package org.openfsp.gateway.controllers.discovery

import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.controllers.webhooks.JwkResource
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.webhook.SignatureParams
import org.springframework.stereotype.Component
import java.net.URI

@Component
class ServiceConfigurationMapper {
    fun toResource(
        publicBaseUrl: URI,
        capabilities: List<String>,
        webhookKeys: List<JwkResource>,
    ): ServiceConfiguration {
        val issuer = publicBaseUrl.toString().trimEnd('/')
        val api = "$issuer/v1"
        return ServiceConfiguration(
            issuer = issuer,
            protocolVersion = ProtocolVersion.CURRENT,
            apiBase = api,
            paymentsEndpoint = "$api/payments",
            capabilitiesEndpoint = "$api/capabilities",
            statementsEndpoint = "$api/statements",
            statementEntriesEndpoint = "$api/statement_entries",
            gatewayStatementsEndpoint = "$api/gateway_statements",
            webhookKeysUri = "$api/webhooks/keys",
            webhookKeys = webhookKeys,
            authenticationMethodsSupported = listOf(BEARER),
            scopesSupported = Scope.entries.map { it.wire },
            capabilitiesSupported = capabilities,
            webhookSignatureAlgorithmsSupported = listOf(SignatureParams.ED25519),
        )
    }

    private companion object {
        const val BEARER = "bearer"
    }
}

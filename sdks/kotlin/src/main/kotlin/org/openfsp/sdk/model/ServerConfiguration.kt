package org.openfsp.sdk.model

import org.openfsp.protocol.webhook.KeyStatus

data class WebhookKey(
    val keyId: String,
    val keyType: String,
    val curve: String?,
    val algorithm: String?,
    val use: String?,
    val x: String?,
    val status: KeyStatus?,
)

data class ServerConfiguration(
    val issuer: String,
    val protocolVersion: String,
    val capabilitiesSupported: List<String>,
    val apiBase: String?,
    val paymentsEndpoint: String?,
    val capabilitiesEndpoint: String?,
    val statementsEndpoint: String?,
    val statementEntriesEndpoint: String?,
    val webhookKeys: List<WebhookKey>,
    val authenticationMethodsSupported: List<String>,
    val scopesSupported: List<String>,
    val webhookSignatureAlgorithmsSupported: List<String>,
) {
    fun supports(capability: String): Boolean = capability in capabilitiesSupported
}

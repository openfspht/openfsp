package org.openfsp.gateway.discovery

import org.openfsp.protocol.webhook.WebhookJwk

data class ServiceConfiguration(
    val issuer: String,
    val protocolVersion: String,
    val apiBase: String,
    val paymentsEndpoint: String,
    val capabilitiesEndpoint: String,
    val statementsEndpoint: String,
    val statementEntriesEndpoint: String,
    val gatewayStatementsEndpoint: String,
    val webhookKeys: List<WebhookJwk>,
    val authenticationMethodsSupported: List<String>,
    val scopesSupported: List<String>,
    val capabilitiesSupported: List<String>,
    val webhookSignatureAlgorithmsSupported: List<String>,
)

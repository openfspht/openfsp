package org.openfsp.gateway.controllers.discovery


data class ServiceConfiguration(
    val issuer: String,
    val protocolVersion: String,
    val apiBase: String,
    val paymentsEndpoint: String,
    val capabilitiesEndpoint: String,
    val statementsEndpoint: String,
    val statementEntriesEndpoint: String,
    val gatewayStatementsEndpoint: String,
    val webhookKeys: List<JwkResource>,
    val authenticationMethodsSupported: List<String>,
    val scopesSupported: List<String>,
    val capabilitiesSupported: List<String>,
    val webhookSignatureAlgorithmsSupported: List<String>,
)

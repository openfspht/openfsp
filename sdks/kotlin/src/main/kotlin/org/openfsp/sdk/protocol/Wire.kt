package org.openfsp.sdk.protocol

import com.fasterxml.jackson.annotation.JsonProperty

internal data class WireConfiguration(
    val issuer: String,
    @JsonProperty("protocol_version") val protocolVersion: String,
    @JsonProperty("capabilities_supported") val capabilitiesSupported: List<String>,
    @JsonProperty("api_base") val apiBase: String? = null,
    @JsonProperty("payments_endpoint") val paymentsEndpoint: String? = null,
    @JsonProperty("capabilities_endpoint") val capabilitiesEndpoint: String? = null,
    @JsonProperty("statements_endpoint") val statementsEndpoint: String? = null,
    @JsonProperty("webhook_keys") val webhookKeys: List<WireWebhookKey>? = null,
    @JsonProperty("authentication_methods_supported") val authenticationMethodsSupported: List<String>? = null,
    @JsonProperty("scopes_supported") val scopesSupported: List<String>? = null,
    @JsonProperty("webhook_signature_algorithms_supported") val webhookSignatureAlgorithmsSupported: List<String>? = null,
    @JsonProperty("conformance_endpoint") val conformanceEndpoint: String? = null,
)

internal data class WireWebhookKey(
    val kid: String,
    val kty: String,
    val crv: String? = null,
    val alg: String? = null,
    val use: String? = null,
    val x: String? = null,
    val y: String? = null,
    @JsonProperty("openfsp_status") val openfspStatus: String? = null,
)

package org.openfsp.server.api

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ConformanceFixturesResource(
    @JsonProperty("api_key") val apiKey: String,
    @JsonProperty("expires_at") val expiresAt: Instant,
    val inputs: Map<String, String> = emptyMap(),
    @JsonProperty("control_endpoint") val controlEndpoint: String? = null,
)

data class ControlledPaymentResource(
    val payment: String,
    val settled: Boolean,
    @JsonProperty("callbacks_sent") val callbacksSent: Int,
)

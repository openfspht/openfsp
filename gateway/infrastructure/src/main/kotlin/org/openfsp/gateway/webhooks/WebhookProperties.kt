package org.openfsp.gateway.webhooks

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("openfsp.webhooks")
data class WebhookProperties(
    val endpoints: Map<String, Endpoint> = emptyMap(),
    val pollInterval: Duration = Duration.ofMillis(500),
    val maxPendingPerEndpoint: Int = 10_000,
    val disableAfterAbandoned: Int = 3,
) {
    data class Endpoint(
        val url: URI,
        val events: Set<String>,
        val enabled: Boolean = true,
    )
}

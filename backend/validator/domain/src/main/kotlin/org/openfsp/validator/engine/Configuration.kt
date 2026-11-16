package org.openfsp.validator.engine

import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.spi.WebhookInbox
import org.openfsp.validator.suite.TargetKind
import java.nio.file.Path
import java.time.Duration

data class Configuration(
    val target: TargetKind,
    val baseUrl: String,
    val apiKey: String,
    val simulatorUrl: String?,
    val provider: String? = null,
    val conformance: Path,
    val inputs: Map<String, String> = emptyMap(),
    val only: String? = null,
    val timeout: Duration = Duration.ofSeconds(30),
    val webhookInbox: WebhookInbox? = null,
) {
    fun redacted(): Map<String, Any?> =
        linkedMapOf(
            "target" to target.wire,
            "base_url" to baseUrl,
            "api_key" to ExchangeRedaction.MASK,
            "simulator_url" to simulatorUrl,
            "provider" to provider,
            "conformance" to conformance.toString(),
            "inputs" to inputs.keys.sorted().associateWith { ExchangeRedaction.MASK },
            "only" to only,
            "timeout" to timeout.toString(),
            "webhook_url" to webhookInbox?.url,
        )

    val secrets: List<String> get() = listOf(apiKey) + inputs.values
}

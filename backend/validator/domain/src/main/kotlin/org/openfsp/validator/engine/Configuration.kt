package org.openfsp.validator.engine

import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.spi.WebhookInbox
import org.openfsp.validator.suite.TargetKind
import java.time.Duration

data class Configuration(
    val target: TargetKind,
    val baseUrl: String,
    val apiKey: String,
    val controlEndpoint: String? = null,
    val provider: String? = null,
    val inputs: Map<String, String> = emptyMap(),
    val only: String? = null,
    val timeout: Duration = Duration.ofSeconds(30),
    val webhookInbox: WebhookInbox? = null,
    val preparedBy: String = OPERATOR,
) {
    fun redacted(suiteVersion: String): Map<String, Any?> =
        linkedMapOf(
            TARGET to target.wire,
            BASE_URL to baseUrl,
            "api_key" to ExchangeRedaction.MASK,
            "provider" to provider,
            "conformance" to "openfsp conformance suite $suiteVersion",
            "inputs" to inputs.keys.sorted().associateWith { ExchangeRedaction.MASK },
            "only" to only,
            "timeout" to timeout.toString(),
            "webhook_url" to webhookInbox?.url,
            "prepared_by" to preparedBy,
            "outcomes" to controlEndpoint?.let { SUITE },
        )

    val secrets: List<String> get() = listOf(apiKey) + inputs.values

    companion object {
        const val TARGET = "target"
        const val BASE_URL = "base_url"
        const val OPERATOR = "operator"
        const val SERVER = "server"
        const val SUITE = "suite"
    }
}

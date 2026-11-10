package org.openfsp.simulator.dialects.openfsp

import org.openfsp.simulator.configuration.SimulatorProperties
import org.openfsp.simulator.dialects.DialectCallbacks
import org.openfsp.simulator.dialects.OperatorWebhookClient
import org.openfsp.simulator.dialects.hmacSha256Hex
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.operator.OperatorCallback
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class OpenFspCallbacks(
    private val client: OperatorWebhookClient,
    private val mapper: OpenFspMapper,
    private val json: JsonMapper,
    private val properties: SimulatorProperties,
) : DialectCallbacks {
    override val dialect = Dialect.OPENFSP

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean {
        val body = json.writeValueAsBytes(mapper.toWire(callback))
        val secret = properties.operator(operator).credentialOrNull(CALLBACK_SECRET)
        val headers = secret?.let { mapOf(SIGNATURE_HEADER to "sha256=${hmacSha256Hex(it, body)}") } ?: emptyMap()
        return client.post(url, body, headers)
    }

    companion object {
        const val SIGNATURE_HEADER = "X-Mock-Signature"
        const val CALLBACK_SECRET = "callback-secret"
    }
}

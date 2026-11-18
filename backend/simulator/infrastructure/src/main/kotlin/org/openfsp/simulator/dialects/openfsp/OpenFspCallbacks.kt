package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.simulator.callbacks.DialectCallbacks
import org.openfsp.simulator.callbacks.OperatorWebhookClient
import org.openfsp.simulator.operator.Dialect
import org.openfsp.simulator.settlement.OperatorCallback
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class OpenFspCallbacks(
    private val client: OperatorWebhookClient,
    private val mapper: OpenFspMapper,
    private val keys: OpenFspWebhookKeys,
    private val json: JsonMapper,
) : DialectCallbacks {
    override val dialect = Dialect.OPENFSP

    override fun send(
        operator: String,
        url: String,
        callback: OperatorCallback,
    ): Boolean {
        val event = mapper.toEvent(callback.payment)
        val body = json.writeValueAsBytes(event)
        val signed = keys.signer(operator).sign(url, body, event.id, event.type)
        return client.post(url, body, signed + (WebhookHeaders.DELIVERY_ATTEMPT to (callback.payment.callbacksSent + 1).toString()))
    }
}

package org.openfsp.simulator.dialects.openfsp

import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.simulator.callbacks.OperatorWebhookClient
import org.openfsp.simulator.emission.DeliveryResult
import org.openfsp.simulator.emission.EncodedEvent
import org.openfsp.simulator.emission.spi.EventEncoder
import org.openfsp.simulator.emission.spi.EventTransport
import org.openfsp.simulator.payment.SimulatedPayment
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class OpenFspEvents(
    private val mapper: OpenFspMapper,
    private val keys: OpenFspWebhookKeys,
    private val client: OperatorWebhookClient,
    private val json: JsonMapper,
) : EventEncoder,
    EventTransport {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun encode(payment: SimulatedPayment): EncodedEvent {
        val event = mapper.toEvent(payment)
        return EncodedEvent(event.id, event.type, json.writeValueAsBytes(event))
    }

    override fun deliver(
        operator: String,
        url: String,
        event: EncodedEvent,
        attempt: Int,
    ): DeliveryResult {
        val signed = keys.signer(operator).sign(url, event.body, event.id, event.type)
        return client.deliver(url, event.body, signed + (WebhookHeaders.DELIVERY_ATTEMPT to attempt.toString()))
    }

    override fun abandoned(
        url: String,
        event: EncodedEvent,
        attempt: Int,
    ) {
        log.warn("Abandoned the delivery of event {} to {} after attempt {}", event.id, url, attempt)
    }
}

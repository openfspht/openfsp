package org.openfsp.gateway.webhooks.resources

import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.protocol.api.PaymentEvent
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper

@Component
class EventBodyMapper(
    private val payments: PaymentResourceMapper,
    private val json: JsonMapper,
) {
    fun toJson(event: WebhookEvent): ByteArray =
        json.writeValueAsBytes(
            PaymentEvent(
                id = event.id,
                type = event.type,
                createdAt = event.createdAt,
                sequence = event.sequence.toLong(),
                resourceType = PaymentEvent.PAYMENT,
                resourceId = event.payment.id,
                reference = event.payment.reference,
                previousStatus = event.previousStatus?.wire,
                data = payments.toResource(event.payment),
            ),
        )
}

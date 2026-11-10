package org.openfsp.gateway.webhooks

import org.openfsp.gateway.controllers.payments.PaymentResource
import org.openfsp.gateway.controllers.payments.PaymentResourceMapper
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import java.time.Instant

data class EventBody(
    val id: String,
    val type: String,
    val createdAt: Instant,
    val sequence: Int,
    val resourceType: String,
    val resourceId: String,
    val reference: String,
    val previousStatus: PaymentStatus?,
    val data: PaymentResource,
)

@Component
class EventBodyMapper(
    private val payments: PaymentResourceMapper,
    private val json: JsonMapper,
) {
    fun toJson(event: WebhookEvent): ByteArray =
        json.writeValueAsBytes(
            EventBody(
                id = event.id,
                type = event.type,
                createdAt = event.createdAt,
                sequence = event.sequence,
                resourceType = RESOURCE_TYPE,
                resourceId = event.payment.id,
                reference = event.payment.reference,
                previousStatus = event.previousStatus,
                data = payments.toResource(event.payment),
            ),
        )

    private companion object {
        const val RESOURCE_TYPE = "payment"
    }
}

package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.gateway.webhook.spi.Events

@Stub
class InMemoryEvents : Events {
    val saved = mutableListOf<WebhookEvent>()

    override fun nextSequence(paymentId: String): Int = saved.count { it.payment.id == paymentId } + 1

    override fun save(event: WebhookEvent) {
        saved += event
    }
}

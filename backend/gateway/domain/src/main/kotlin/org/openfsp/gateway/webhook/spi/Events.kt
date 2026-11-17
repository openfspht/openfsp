package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.WebhookEvent

interface Events {
    fun nextSequence(paymentId: String): Int

    fun save(event: WebhookEvent)
}

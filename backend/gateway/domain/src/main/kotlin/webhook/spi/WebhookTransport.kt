package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.TransportResult

interface WebhookTransport {
    fun send(
        delivery: Delivery,
        attempt: Int,
    ): TransportResult
}

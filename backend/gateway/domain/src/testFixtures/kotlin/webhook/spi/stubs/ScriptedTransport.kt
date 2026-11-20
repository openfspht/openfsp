package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.spi.WebhookTransport

@Stub
class ScriptedTransport(
    var result: TransportResult = TransportResult(204, null, null, 1),
) : WebhookTransport {
    val sent = mutableListOf<Pair<Delivery, Int>>()

    override fun send(
        delivery: Delivery,
        attempt: Int,
    ): TransportResult {
        sent += delivery to attempt
        return result
    }
}

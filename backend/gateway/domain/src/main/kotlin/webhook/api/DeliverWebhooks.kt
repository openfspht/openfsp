package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.webhook.Delivery

interface DeliverWebhooks {
    fun claimDue(limit: Int): List<Delivery>

    fun deliver(delivery: Delivery)
}

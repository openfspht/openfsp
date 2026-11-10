package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.webhook.Delivery

interface RecordPaymentEvents {
    fun record(transition: PaymentTransitioned)
}

interface DeliverWebhooks {
    fun claimDue(limit: Int): List<Delivery>

    fun deliver(delivery: Delivery)
}

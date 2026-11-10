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

interface RotateSigningKeys {
    fun current(): String

    fun published(): List<org.openfsp.gateway.webhook.PublishedSigningKey>

    fun rotate(immediate: Boolean): String
}

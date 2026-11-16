package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.payment.PaymentTransitioned
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.Subscription
import java.net.URI
import java.time.Instant

interface RecordPaymentEvents {
    fun record(transition: PaymentTransitioned)
}

interface DeliverWebhooks {
    fun claimDue(limit: Int): List<Delivery>

    fun deliver(delivery: Delivery)
}

interface ManageSubscriptions {
    fun add(
        name: String,
        url: URI,
        events: Set<String>,
    ): Subscription

    fun remove(name: String): Boolean
}

interface RotateSigningKeys {
    fun current(): String

    fun published(): List<org.openfsp.gateway.webhook.PublishedSigningKey>

    fun rotate(immediate: Boolean): String

    fun rotateIfDue(): String?

    fun nextRotationAt(): Instant?
}

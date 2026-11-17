package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.DeliveryAttempt
import org.openfsp.gateway.webhook.Subscription
import java.time.Duration
import java.time.Instant

interface Deliveries {
    fun enqueue(
        eventId: String,
        subscription: Subscription,
        at: Instant,
    )

    fun pending(endpoint: String): Long

    fun stopPending(
        endpoint: String,
        at: Instant,
    ): Int

    fun claimDue(
        now: Instant,
        lease: Duration,
        limit: Int,
    ): List<Delivery>

    fun record(attempt: DeliveryAttempt)

    fun finish(
        deliveryId: Long,
        status: String,
        attempts: Int,
        at: Instant,
    )

    fun retry(
        deliveryId: Long,
        attempts: Int,
        nextAttemptAt: Instant,
        now: Instant,
    )
}

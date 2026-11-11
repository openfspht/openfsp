package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.DeliveryAttempt
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.WebhookEvent
import java.net.InetAddress
import java.time.Duration
import java.time.Instant

interface Events {
    fun nextSequence(paymentId: String): Int

    fun save(event: WebhookEvent)
}

interface Subscriptions {
    fun all(): List<Subscription>
}

interface SubscriptionStore {
    fun all(): List<Subscription>

    fun save(subscription: Subscription)

    fun delete(name: String): Boolean
}

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
        at: Instant,
    )
}

interface EndpointHealth {
    fun isDisabled(endpoint: String): Boolean

    fun succeeded(endpoint: String)

    fun abandoned(endpoint: String): Int

    fun disable(
        endpoint: String,
        reason: String,
        at: Instant,
    )

    fun forget(endpoint: String)
}

fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>
}

interface WebhookTransport {
    fun send(
        delivery: Delivery,
        attempt: Int,
    ): TransportResult
}

interface SigningKeyStore {
    fun all(): List<org.openfsp.gateway.webhook.SigningKey>

    fun create(activatesAt: Instant): String

    fun retire(
        kid: String,
        at: Instant,
    )

    fun withdraw(kid: String)

    fun lockRotation(): Boolean
}

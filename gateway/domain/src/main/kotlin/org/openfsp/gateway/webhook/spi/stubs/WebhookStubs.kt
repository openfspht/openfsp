package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.SigningKey
import org.openfsp.gateway.webhook.DeliveryAttempt
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.WebhookEvent
import org.openfsp.gateway.webhook.spi.Deliveries
import org.openfsp.gateway.webhook.spi.EndpointHealth
import org.openfsp.gateway.webhook.spi.Events
import org.openfsp.gateway.webhook.spi.SigningKeyStore
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhook.spi.WebhookTransport
import java.time.Duration
import java.time.Instant

@Stub
class InMemoryEvents : Events {
    val saved = mutableListOf<WebhookEvent>()

    override fun nextSequence(paymentId: String): Int = saved.count { it.payment.id == paymentId } + 1

    override fun save(event: WebhookEvent) {
        saved += event
    }
}

@Stub
class FixedSubscriptions(
    private val subscriptions: List<Subscription> = emptyList(),
) : Subscriptions {
    override fun all(): List<Subscription> = subscriptions
}

@Stub
class InMemoryDeliveries : Deliveries {
    data class Queued(
        val delivery: Delivery,
        val status: String,
        val nextAttemptAt: Instant?,
    )

    val queued = mutableMapOf<Long, Queued>()
    val attempts = mutableListOf<DeliveryAttempt>()
    private var sequence = 0L

    override fun enqueue(
        eventId: String,
        subscription: Subscription,
        at: Instant,
    ) {
        val id = ++sequence
        queued[id] = Queued(Delivery(id, eventId, "payment.created", subscription.name, subscription.url, 0, ByteArray(0)), "pending", at)
    }

    override fun pending(endpoint: String): Long = queued.values.count { it.delivery.endpoint == endpoint && it.status == "pending" }.toLong()

    override fun claimDue(
        now: Instant,
        lease: Duration,
        limit: Int,
    ): List<Delivery> =
        queued.values
            .filter { it.status == "pending" && it.nextAttemptAt?.isAfter(now) == false }
            .take(limit)
            .map { it.delivery }

    override fun record(attempt: DeliveryAttempt) {
        attempts += attempt
    }

    override fun finish(
        deliveryId: Long,
        status: String,
        attempts: Int,
        at: Instant,
    ) {
        queued.computeIfPresent(deliveryId) { _, it -> it.copy(delivery = it.delivery.copy(attempts = attempts), status = status, nextAttemptAt = null) }
    }

    override fun retry(
        deliveryId: Long,
        attempts: Int,
        at: Instant,
    ) {
        queued.computeIfPresent(deliveryId) { _, it -> it.copy(delivery = it.delivery.copy(attempts = attempts), nextAttemptAt = at) }
    }
}

@Stub
class InMemoryEndpointHealth : EndpointHealth {
    private val abandoned = mutableMapOf<String, Int>()
    val disabled = mutableMapOf<String, String>()

    override fun isDisabled(endpoint: String): Boolean = endpoint in disabled

    override fun succeeded(endpoint: String) {
        abandoned[endpoint] = 0
    }

    override fun abandoned(endpoint: String): Int = abandoned.merge(endpoint, 1, Int::plus)!!

    override fun disable(
        endpoint: String,
        reason: String,
        at: Instant,
    ) {
        disabled[endpoint] = reason
    }
}

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

@Stub
class InMemorySigningKeys : SigningKeyStore {
    val keys = mutableListOf<SigningKey>()

    override fun all(): List<SigningKey> = keys.toList()

    override fun create(activatesAt: Instant): String = "gw-${keys.size + 1}".also { keys += SigningKey(it, activatesAt, null) }

    override fun retire(
        kid: String,
        at: Instant,
    ) = replace(kid) { it.copy(retiredAt = at) }

    override fun withdraw(kid: String) = replace(kid) { it.copy(retiredAt = Instant.EPOCH) }

    private fun replace(
        kid: String,
        change: (SigningKey) -> SigningKey,
    ) {
        keys.replaceAll { if (it.kid == kid) change(it) else it }
    }
}

@Stub
class InMemorySubscriptionStore : SubscriptionStore {
    private val saved = linkedMapOf<String, Subscription>()

    override fun all(): List<Subscription> = saved.values.toList()

    override fun save(subscription: Subscription) {
        saved[subscription.name] = subscription
    }

    override fun delete(name: String): Boolean = saved.remove(name) != null
}

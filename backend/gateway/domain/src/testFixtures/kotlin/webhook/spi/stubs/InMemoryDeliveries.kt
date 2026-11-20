package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.DeliveryAttempt
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.Deliveries
import java.time.Duration
import java.time.Instant

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

    override fun stopPending(
        endpoint: String,
        at: Instant,
    ): Int {
        val stopped = queued.filterValues { it.delivery.endpoint == endpoint && it.status == "pending" }.keys
        stopped.forEach { id -> queued.computeIfPresent(id) { _, it -> it.copy(status = "stopped", nextAttemptAt = null) } }
        return stopped.size
    }

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
        nextAttemptAt: Instant,
        now: Instant,
    ) {
        queued.computeIfPresent(deliveryId) { _, it -> it.copy(delivery = it.delivery.copy(attempts = attempts), nextAttemptAt = nextAttemptAt) }
    }
}

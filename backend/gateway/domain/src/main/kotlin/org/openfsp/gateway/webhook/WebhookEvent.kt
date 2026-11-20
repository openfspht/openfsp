package org.openfsp.gateway.webhook

import org.openfsp.gateway.payment.Payment
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Duration
import java.time.Instant

data class WebhookEvent(
    val id: String,
    val type: String,
    val createdAt: Instant,
    val sequence: Int,
    val previousStatus: PaymentStatus?,
    val payment: Payment,
) {
    companion object {
        fun typeFor(
            previous: PaymentStatus?,
            current: PaymentStatus,
        ): String = "payment." + if (previous == null) "created" else current.wire
    }
}

data class Subscription(
    val account: String,
    val name: String,
    val url: URI,
    val events: Set<String>,
    val enabled: Boolean,
    val managed: Boolean = false,
) {
    fun wants(eventType: String): Boolean = enabled && eventType in events

    fun follows(payment: Payment): Boolean = account == payment.accountId

    companion object {
        val EVENT_TYPES: Set<String> = setOf("payment.created", "payment.succeeded", "payment.failed", "payment.expired", "payment.canceled")
    }
}

data class EndpointRules(
    val developmentMode: Boolean,
)

data class Delivery(
    val id: Long,
    val eventId: String,
    val eventType: String,
    val endpoint: String,
    val url: URI,
    val attempts: Int,
    val body: ByteArray,
) {
    val nextAttempt: Int get() = attempts + 1

    override fun equals(other: Any?): Boolean = other is Delivery && other.id == id && other.attempts == attempts

    override fun hashCode(): Int = id.hashCode()
}

data class TransportResult(
    val statusCode: Int?,
    val error: String?,
    val retryAfter: Duration?,
    val durationMillis: Long,
)

data class DeliveryAttempt(
    val deliveryId: Long,
    val attempt: Int,
    val startedAt: Instant,
    val statusCode: Int?,
    val error: String?,
    val durationMillis: Long,
)

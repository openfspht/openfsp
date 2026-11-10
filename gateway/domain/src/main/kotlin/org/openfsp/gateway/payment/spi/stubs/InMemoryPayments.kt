package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryPayments : Payments {
    private val payments = ConcurrentHashMap<String, Payment>()

    override fun save(payment: Payment): Payment {
        if (payments.values.any { it.reference == payment.reference && it.id != payment.id }) error("reference ${payment.reference} is taken")
        payments[payment.id] = payment
        return payment
    }

    override fun delete(paymentId: String) {
        payments.remove(paymentId)
    }

    override fun find(paymentId: String): Payment? = payments[paymentId]

    override fun lock(paymentId: String): Payment? = payments[paymentId]

    override fun existsWithReference(reference: String): Boolean = payments.values.any { it.reference == reference }

    override fun withReference(reference: String): Payment? = payments.values.firstOrNull { it.reference == reference }

    override fun withOrder(
        provider: String,
        orderId: String,
    ): Payment? = payments.values.firstOrNull { it.provider == provider && it.providerOrderId == orderId }

    override fun withProviderReference(
        provider: String,
        providerReference: String,
    ): Payment? = payments.values.firstOrNull { it.provider == provider && it.providerReference == providerReference }

    override fun withCallbackToken(
        provider: String,
        digest: String,
    ): Payment? = payments.values.firstOrNull { it.provider == provider && it.callbackTokenDigest == digest }

    override fun pendingUnchangedSince(
        instant: Instant,
        limit: Int,
    ): List<Payment> = payments.values.filter { !it.isTerminal && it.updatedAt.isBefore(instant) }.sortedBy { it.updatedAt }.take(limit)

    override fun pendingExpiringBefore(
        provider: String,
        instant: Instant,
    ): List<Payment> = payments.values.filter { it.provider == provider && !it.isTerminal && it.expiresAt?.isBefore(instant) == true }

    override fun completedBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        payments.values
            .filter { it.provider == provider && it.completedAt?.let { at -> !at.isBefore(from) && at.isBefore(until) } == true }
            .sortedWith(compareBy({ it.completedAt }, { it.id }))
}

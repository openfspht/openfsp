package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.Payments
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryPayments : Payments {
    private val payments = ConcurrentHashMap<String, Payment>()
    private val checkedAt = ConcurrentHashMap<String, Instant>()

    override fun save(payment: Payment): Payment {
        if (payments.values.any { it.reference == payment.reference && it.id != payment.id }) throw DuplicateReference()
        check(payments[payment.id]?.version == payment.version) { "payment ${payment.id} was saved from a stale read" }
        return payment.copy(version = (payment.version ?: -1) + 1).also { payments[it.id] = it }
    }

    override fun delete(paymentId: String) {
        payments.remove(paymentId)
    }

    override fun find(paymentId: String): Payment? = payments[paymentId]

    override fun lock(paymentId: String): Payment? = payments[paymentId]

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

    override fun dueForReconciliation(
        providers: Set<String>,
        checkedBefore: Instant,
        inFlightSince: Instant,
        limit: Int,
    ): List<Payment> =
        payments.values
            .filter { !it.isTerminal && it.provider in providers && lastCheck(it).isBefore(checkedBefore) }
            .filterNot { it.isCreationInFlight && !it.createdAt.isBefore(inFlightSince) }
            .sortedBy(::lastCheck)
            .take(limit)

    override fun markChecked(
        paymentId: String,
        at: Instant,
    ) {
        checkedAt[paymentId] = at
    }

    private fun lastCheck(payment: Payment): Instant = checkedAt[payment.id] ?: payment.updatedAt

    override fun pendingExpiringBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        payments.values.filter { it.provider == provider && !it.isTerminal && it.expiresAt?.let { at -> !at.isBefore(from) && at.isBefore(until) } == true }

    override fun completedBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        payments.values
            .filter { it.provider == provider && it.completedAt?.let { at -> !at.isBefore(from) && at.isBefore(until) } == true }
            .sortedWith(compareBy({ it.completedAt }, { it.id }))
}

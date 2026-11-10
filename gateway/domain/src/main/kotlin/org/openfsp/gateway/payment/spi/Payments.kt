package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.payment.Payment
import java.time.Instant

interface Payments {
    fun save(payment: Payment): Payment

    fun delete(paymentId: String)

    fun find(paymentId: String): Payment?

    fun lock(paymentId: String): Payment?

    fun existsWithReference(reference: String): Boolean

    fun withReference(reference: String): Payment?

    fun withOrder(
        provider: String,
        orderId: String,
    ): Payment?

    fun withProviderReference(
        provider: String,
        providerReference: String,
    ): Payment?

    fun withCallbackToken(
        provider: String,
        digest: String,
    ): Payment?

    fun pendingUnchangedSince(
        instant: Instant,
        limit: Int,
    ): List<Payment>

    fun pendingExpiringBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment>

    fun completedBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment>
}

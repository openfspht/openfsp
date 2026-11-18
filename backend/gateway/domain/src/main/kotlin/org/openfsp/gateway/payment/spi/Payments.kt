package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.statement.StatementCursor
import java.time.Instant

interface Payments {
    fun save(payment: Payment): Payment

    fun delete(paymentId: String)

    fun find(paymentId: String): Payment?

    fun lock(paymentId: String): Payment?

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

    fun dueForReconciliation(
        providers: Set<String>,
        checkedBefore: Instant,
        inFlightSince: Instant,
        limit: Int,
    ): List<Payment>

    fun markChecked(
        paymentId: String,
        at: Instant,
    )

    fun succeededBetween(
        provider: String,
        from: Instant,
        until: Instant,
        after: StatementCursor?,
        limit: Int,
    ): List<Payment>
}

package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.ReconciliationDue
import org.openfsp.gateway.payment.ReconciliationTurn
import org.openfsp.gateway.statement.StatementCursor
import java.time.Instant

interface Payments {
    fun save(payment: Payment): Payment

    fun delete(paymentId: String)

    fun find(paymentId: String): Payment?

    fun findOfAccount(
        account: String,
        paymentId: String,
    ): Payment?

    fun lock(paymentId: String): Payment?

    fun withReference(
        account: String,
        reference: String,
    ): Payment?

    fun withOrder(
        provider: String,
        orderId: String,
    ): Payment?

    fun withCallbackToken(
        provider: String,
        digest: String,
    ): Payment?

    fun claimForReconciliation(
        due: ReconciliationDue,
        limit: Int,
    ): List<ReconciliationTurn>

    fun scheduleCheck(
        paymentId: String,
        at: Instant,
        attempts: Int,
    )

    fun succeededBetween(
        accounts: AccountFilter,
        provider: String,
        from: Instant,
        until: Instant,
        after: StatementCursor?,
        limit: Int,
    ): List<Payment>
}

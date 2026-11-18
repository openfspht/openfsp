package org.openfsp.gateway.payment.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.ReconciliationDue
import org.openfsp.gateway.payment.ReconciliationTurn
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.protocol.PaymentStatus
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryPayments : Payments {
    private val payments = ConcurrentHashMap<String, Payment>()
    private val nextCheckAt = ConcurrentHashMap<String, Instant>()
    private val attempts = ConcurrentHashMap<String, Int>()

    override fun save(payment: Payment): Payment {
        if (payments.values.any { it.accountId == payment.accountId && it.reference == payment.reference && it.id != payment.id }) throw DuplicateReference()
        check(payments[payment.id]?.version == payment.version) { "payment ${payment.id} was saved from a stale read" }
        return payment.copy(version = (payment.version ?: -1) + 1).also { payments[it.id] = it }
    }

    override fun delete(paymentId: String) {
        payments.remove(paymentId)
    }

    override fun find(paymentId: String): Payment? = payments[paymentId]

    override fun findOfAccount(
        account: String,
        paymentId: String,
    ): Payment? = payments[paymentId]?.takeIf { it.accountId == account }

    override fun lock(paymentId: String): Payment? = payments[paymentId]

    override fun withReference(
        account: String,
        reference: String,
    ): Payment? = payments.values.firstOrNull { it.accountId == account && it.reference == reference }

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

    override fun claimForReconciliation(
        due: ReconciliationDue,
        limit: Int,
    ): List<ReconciliationTurn> {
        val claimed =
            payments.values
                .filter { !it.isTerminal && it.provider in due.providers && isDue(it, due) }
                .filterNot { it.isCreationInFlight && !it.createdAt.isBefore(due.inFlightSince) }
                .sortedBy { nextCheckAt[it.id] ?: it.updatedAt }
                .take(limit)
        claimed.forEach { nextCheckAt[it.id] = due.claimedUntil }
        return claimed.map { ReconciliationTurn(it, attempts[it.id] ?: 0) }
    }

    private fun isDue(
        payment: Payment,
        due: ReconciliationDue,
    ): Boolean = nextCheckAt[payment.id]?.let { !it.isAfter(due.now) } ?: payment.updatedAt.isBefore(due.quietBefore)

    override fun scheduleCheck(
        paymentId: String,
        at: Instant,
        attempts: Int,
    ) {
        nextCheckAt[paymentId] = at
        this.attempts[paymentId] = attempts
    }

    override fun succeededBetween(
        accounts: AccountFilter,
        provider: String,
        from: Instant,
        until: Instant,
        after: StatementCursor?,
        limit: Int,
    ): List<Payment> =
        payments.values
            .filter { accounts.admits(it.accountId) && it.provider == provider && it.status == PaymentStatus.SUCCEEDED && booked(it)?.let { at -> !at.isBefore(from) && at.isBefore(until) } == true }
            .sortedWith(compareBy({ booked(it) }, { it.id }))
            .filter { after == null || compareValuesBy(booked(it) to it.id, after.bookedAt to after.paymentId, { it.first }, { it.second }) > 0 }
            .take(limit)

    private fun booked(payment: Payment): Instant? = payment.bookedAt ?: payment.completedAt
}

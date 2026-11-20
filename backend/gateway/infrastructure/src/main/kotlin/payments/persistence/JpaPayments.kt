package org.openfsp.gateway.payments.persistence

import org.hibernate.exception.ConstraintViolationException
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.ReconciliationDue
import org.openfsp.gateway.payment.ReconciliationTurn
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.protocol.PaymentStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Component
class JpaPayments(
    private val repository: PaymentJpaRepository,
    private val mapper: PaymentEntityMapper,
) : Payments {
    override fun save(payment: Payment): Payment =
        try {
            mapper.toDomain(repository.saveAndFlush(mapper.toEntity(payment)))
        } catch (exception: DataIntegrityViolationException) {
            if (exception.violates(REFERENCE_CONSTRAINT)) throw DuplicateReference()
            throw exception
        }

    private fun Throwable.violates(constraint: String): Boolean =
        generateSequence(this) { it.cause }.filterIsInstance<ConstraintViolationException>().any { it.constraintName.equals(constraint, ignoreCase = true) }

    override fun delete(paymentId: String) = repository.deleteById(paymentId)

    override fun find(paymentId: String): Payment? = repository.findById(paymentId).map(mapper::toDomain).orElse(null)

    override fun findOfAccount(
        account: String,
        paymentId: String,
    ): Payment? = repository.findByIdAndAccountId(paymentId, account)?.let(mapper::toDomain)

    override fun lock(paymentId: String): Payment? = repository.lockById(paymentId)?.let(mapper::toDomain)

    override fun withReference(
        account: String,
        reference: String,
    ): Payment? = repository.findByAccountIdAndReference(account, reference)?.let(mapper::toDomain)

    override fun withOrder(
        provider: String,
        orderId: String,
    ): Payment? = repository.findByProviderAndProviderOrderId(provider, orderId)?.let(mapper::toDomain)

    override fun withCallbackToken(
        provider: String,
        digest: String,
    ): Payment? = repository.findByProviderAndCallbackTokenDigest(provider, mapper.digest(digest))?.let(mapper::toDomain)

    @Transactional
    override fun claimForReconciliation(
        due: ReconciliationDue,
        limit: Int,
    ): List<ReconciliationTurn> {
        if (due.providers.isEmpty()) return emptyList()
        val locked = repository.lockDueForReconciliation(due.providers, due.now, due.quietBefore, due.inFlightSince, limit)
        if (locked.isEmpty()) return emptyList()
        val attempts = locked.associate { (id, count) -> id as String to (count as Number).toInt() }
        repository.claimUntil(attempts.keys, due.claimedUntil)
        return repository.findAllById(attempts.keys).map { ReconciliationTurn(mapper.toDomain(it), attempts.getValue(it.id)) }
    }

    override fun scheduleCheck(
        paymentId: String,
        at: Instant,
        attempts: Int,
    ) = repository.scheduleCheck(paymentId, at, attempts)

    override fun succeededBetween(
        accounts: AccountFilter,
        provider: String,
        from: Instant,
        until: Instant,
        after: StatementCursor?,
        limit: Int,
    ): List<Payment> {
        val account =
            when (accounts) {
                AccountFilter.Every -> null
                is AccountFilter.Only -> accounts.id
            }
        val rows =
            if (after == null) {
                repository.succeededBetween(account, provider, SUCCEEDED, from, until, Limit.of(limit))
            } else {
                repository.succeededBetweenAfter(account, provider, SUCCEEDED, from, until, after.bookedAt, after.paymentId, Limit.of(limit))
            }
        return rows.map(mapper::toDomain)
    }

    private companion object {
        const val REFERENCE_CONSTRAINT = "payments_account_reference_key"
        val SUCCEEDED = PaymentStatus.SUCCEEDED.wire
    }
}

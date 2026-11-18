package org.openfsp.gateway.payments.persistence

import org.hibernate.exception.ConstraintViolationException
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.DuplicateReference
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.protocol.PaymentStatus
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
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

    override fun lock(paymentId: String): Payment? = repository.lockById(paymentId)?.let(mapper::toDomain)

    override fun withReference(
        account: String,
        reference: String,
    ): Payment? = repository.findByAccountIdAndReference(account, reference)?.let(mapper::toDomain)

    override fun withOrder(
        provider: String,
        orderId: String,
    ): Payment? = repository.findByProviderAndProviderOrderId(provider, orderId)?.let(mapper::toDomain)

    override fun withProviderReference(
        provider: String,
        providerReference: String,
    ): Payment? = repository.findFirstByProviderAndProviderReference(provider, providerReference)?.let(mapper::toDomain)

    override fun withCallbackToken(
        provider: String,
        digest: String,
    ): Payment? = repository.findByProviderAndCallbackTokenDigest(provider, mapper.digest(digest))?.let(mapper::toDomain)

    override fun dueForReconciliation(
        providers: Set<String>,
        checkedBefore: Instant,
        inFlightSince: Instant,
        limit: Int,
    ): List<Payment> =
        if (providers.isEmpty()) emptyList() else repository.dueForReconciliation(providers, checkedBefore, inFlightSince, limit).map(mapper::toDomain)

    override fun markChecked(
        paymentId: String,
        at: Instant,
    ) = repository.markChecked(paymentId, at)

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

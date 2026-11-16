package org.openfsp.gateway.payments.persistence

import org.openfsp.gateway.payment.DuplicateReference
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
import org.hibernate.exception.ConstraintViolationException
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

    override fun withReference(reference: String): Payment? = repository.findByReference(reference)?.let(mapper::toDomain)

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

    override fun pendingUnchangedSince(
        instant: Instant,
        limit: Int,
    ): List<Payment> = repository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAt(PENDING, instant, Limit.of(limit)).map(mapper::toDomain)

    override fun pendingExpiringBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        repository
            .findByProviderAndStatusAndExpiresAtGreaterThanEqualAndExpiresAtLessThan(provider, PENDING, from, until)
            .map(mapper::toDomain)

    override fun completedBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        repository
            .findByProviderAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscIdAsc(provider, from, until)
            .map(mapper::toDomain)

    private companion object {
        const val REFERENCE_CONSTRAINT = "payments_reference_key"
        val PENDING = PaymentStatus.PENDING.wire
    }
}

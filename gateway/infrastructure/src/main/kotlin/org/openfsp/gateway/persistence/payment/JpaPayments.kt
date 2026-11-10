package org.openfsp.gateway.persistence.payment

import org.openfsp.gateway.payment.DuplicateReference
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
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
        } catch (_: DataIntegrityViolationException) {
            throw DuplicateReference()
        }

    override fun delete(paymentId: String) = repository.deleteById(paymentId)

    override fun find(paymentId: String): Payment? = repository.findById(paymentId).map(mapper::toDomain).orElse(null)

    override fun lock(paymentId: String): Payment? = repository.lockById(paymentId)?.let(mapper::toDomain)

    override fun existsWithReference(reference: String): Boolean = repository.existsByReference(reference)

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

    override fun latest(limit: Int): List<Payment> = repository.findByOrderByCreatedAtDesc(Limit.of(limit)).map(mapper::toDomain)

    override fun pendingUnchangedSince(
        instant: Instant,
        limit: Int,
    ): List<Payment> = repository.findByStatusAndUpdatedAtBeforeOrderByUpdatedAt(PENDING, instant, Limit.of(limit)).map(mapper::toDomain)

    override fun pendingExpiringBefore(
        provider: String,
        instant: Instant,
    ): List<Payment> = repository.findByProviderAndStatusAndExpiresAtBefore(provider, PENDING, instant).map(mapper::toDomain)

    override fun completedBetween(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<Payment> =
        repository
            .findByProviderAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscIdAsc(provider, from, until)
            .map(mapper::toDomain)

    private companion object {
        val PENDING = PaymentStatus.PENDING.wire
    }
}

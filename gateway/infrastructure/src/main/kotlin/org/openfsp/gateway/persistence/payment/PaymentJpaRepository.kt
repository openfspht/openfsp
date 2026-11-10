package org.openfsp.gateway.persistence.payment

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface StatusCount {
    val status: String
    val total: Long
    val amount: Long
}

interface PaymentJpaRepository :
    JpaRepository<PaymentJpaEntity, String>,
    JpaSpecificationExecutor<PaymentJpaEntity> {
    @Query("select p.status as status, count(p) as total, coalesce(sum(p.amount), 0) as amount from PaymentJpaEntity p where p.createdAt >= :since group by p.status")
    fun countByStatusSince(since: Instant): List<StatusCount>

    fun existsByReference(reference: String): Boolean

    fun findByReference(reference: String): PaymentJpaEntity?

    fun findByProviderAndProviderOrderId(
        provider: String,
        providerOrderId: String,
    ): PaymentJpaEntity?

    fun findFirstByProviderAndProviderReference(
        provider: String,
        providerReference: String,
    ): PaymentJpaEntity?

    fun findByProviderAndCallbackTokenDigest(
        provider: String,
        callbackTokenDigest: ByteArray,
    ): PaymentJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentJpaEntity p where p.id = :id")
    fun lockById(id: String): PaymentJpaEntity?

    fun findByStatusAndUpdatedAtBeforeOrderByUpdatedAt(
        status: String,
        before: Instant,
        limit: Limit,
    ): List<PaymentJpaEntity>

    fun findByProviderAndStatusAndExpiresAtBefore(
        provider: String,
        status: String,
        before: Instant,
    ): List<PaymentJpaEntity>

    fun findByProviderAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscIdAsc(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<PaymentJpaEntity>
}

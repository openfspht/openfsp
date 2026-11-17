package org.openfsp.gateway.payments.persistence

import jakarta.persistence.LockModeType
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
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

    @Query(
        value = """
            select * from payments
            where status = 'pending' and provider in (:providers) and coalesce(last_checked_at, updated_at) < :checkedBefore
              and (next_action is not null or creation_outcome_unknown or created_at < :inFlightSince)
            order by coalesce(last_checked_at, updated_at)
            limit :limit
        """,
        nativeQuery = true,
    )
    fun dueForReconciliation(
        providers: Collection<String>,
        checkedBefore: Instant,
        inFlightSince: Instant,
        limit: Int,
    ): List<PaymentJpaEntity>

    @Transactional
    @Modifying
    @Query("update payments set last_checked_at = :at where id = :id", nativeQuery = true)
    fun markChecked(
        id: String,
        at: Instant,
    )

    fun findByProviderAndStatusAndExpiresAtGreaterThanEqualAndExpiresAtLessThan(
        provider: String,
        status: String,
        from: Instant,
        until: Instant,
    ): List<PaymentJpaEntity>

    fun findByProviderAndCompletedAtGreaterThanEqualAndCompletedAtLessThanOrderByCompletedAtAscIdAsc(
        provider: String,
        from: Instant,
        until: Instant,
    ): List<PaymentJpaEntity>
}

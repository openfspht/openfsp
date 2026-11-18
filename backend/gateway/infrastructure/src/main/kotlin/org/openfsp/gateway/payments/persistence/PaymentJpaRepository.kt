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

    fun findByAccountIdAndReference(
        accountId: String,
        reference: String,
    ): PaymentJpaEntity?

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
            select id, reconciliation_attempts from payments
            where status = 'pending' and provider in (:providers)
              and ((next_check_at is null and updated_at < :quietBefore) or next_check_at <= :now)
              and (next_action is not null or creation_outcome_unknown or created_at < :inFlightSince)
            order by coalesce(next_check_at, updated_at)
            limit :limit
            for update skip locked
        """,
        nativeQuery = true,
    )
    fun lockDueForReconciliation(
        providers: Collection<String>,
        now: Instant,
        quietBefore: Instant,
        inFlightSince: Instant,
        limit: Int,
    ): List<Array<Any>>

    @Modifying
    @Query("update payments set next_check_at = :at where id in (:ids)", nativeQuery = true)
    fun claimUntil(
        ids: Collection<String>,
        at: Instant,
    ): Int

    @Transactional
    @Modifying
    @Query("update payments set next_check_at = :at, reconciliation_attempts = :attempts where id = :id", nativeQuery = true)
    fun scheduleCheck(
        id: String,
        at: Instant,
        attempts: Int,
    )

    @Query(
        """
            select p from PaymentJpaEntity p
            where (:account is null or p.accountId = :account)
              and p.provider = :provider and p.status = :status and p.completedAt >= :from and p.completedAt < :until
            order by p.completedAt, p.id
        """,
    )
    fun succeededBetween(
        account: String?,
        provider: String,
        status: String,
        from: Instant,
        until: Instant,
        limit: Limit,
    ): List<PaymentJpaEntity>

    @Query(
        """
            select p from PaymentJpaEntity p
            where (:account is null or p.accountId = :account)
              and p.provider = :provider and p.status = :status and p.completedAt >= :from and p.completedAt < :until
              and (p.completedAt > :afterAt or (p.completedAt = :afterAt and p.id > :afterId))
            order by p.completedAt, p.id
        """,
    )
    fun succeededBetweenAfter(
        account: String?,
        provider: String,
        status: String,
        from: Instant,
        until: Instant,
        afterAt: Instant,
        afterId: String,
        limit: Limit,
    ): List<PaymentJpaEntity>
}

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
    fun findByIdAndAccountId(
        id: String,
        accountId: String,
    ): PaymentJpaEntity?

    @Query("SELECT p.status AS status, COUNT(p) AS total, COALESCE(SUM(p.amount), 0) AS amount FROM PaymentJpaEntity p WHERE p.createdAt >= :since GROUP BY p.status")
    fun countByStatusSince(since: Instant): List<StatusCount>

    fun findByAccountIdAndReference(
        accountId: String,
        reference: String,
    ): PaymentJpaEntity?

    fun findByProviderAndProviderOrderId(
        provider: String,
        providerOrderId: String,
    ): PaymentJpaEntity?

    fun findByProviderAndCallbackTokenDigest(
        provider: String,
        callbackTokenDigest: ByteArray,
    ): PaymentJpaEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM PaymentJpaEntity p WHERE p.id = :id")
    fun lockById(id: String): PaymentJpaEntity?

    @Query(
        value = """
            SELECT id, reconciliation_attempts FROM payments
            WHERE status = 'pending' AND provider IN (:providers)
              AND ((next_check_at IS NULL AND updated_at < :quietBefore) OR next_check_at <= :now)
              AND (next_action IS NOT NULL OR creation_outcome_unknown OR created_at < :inFlightSince)
            ORDER BY COALESCE(next_check_at, updated_at)
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
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
    @Query("UPDATE PaymentJpaEntity p SET p.nextCheckAt = :at WHERE p.id IN :ids")
    fun claimUntil(
        ids: Collection<String>,
        at: Instant,
    ): Int

    @Transactional
    @Modifying
    @Query("UPDATE PaymentJpaEntity p SET p.nextCheckAt = :at, p.reconciliationAttempts = :attempts WHERE p.id = :id")
    fun scheduleCheck(
        id: String,
        at: Instant,
        attempts: Int,
    )

    @Query(
        """
            SELECT p FROM PaymentJpaEntity p
            WHERE (:account IS NULL OR p.accountId = :account)
              AND p.provider = :provider AND p.status = :status AND p.bookedAt >= :from AND p.bookedAt < :until
            ORDER BY p.bookedAt, p.id
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
            SELECT p FROM PaymentJpaEntity p
            WHERE (:account IS NULL OR p.accountId = :account)
              AND p.provider = :provider AND p.status = :status AND p.bookedAt >= :from AND p.bookedAt < :until
              AND (p.bookedAt > :afterAt OR (p.bookedAt = :afterAt AND p.id > :afterId))
            ORDER BY p.bookedAt, p.id
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

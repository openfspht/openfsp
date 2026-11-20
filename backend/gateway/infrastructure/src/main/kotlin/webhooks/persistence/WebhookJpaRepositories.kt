package org.openfsp.gateway.webhooks.persistence

import jakarta.persistence.LockModeType
import jakarta.persistence.QueryHint
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.jpa.repository.QueryHints
import java.time.Instant

interface EventJpaRepository : JpaRepository<EventJpaEntity, String> {
    @Query("SELECT COALESCE(MAX(e.sequence), 0) FROM EventJpaEntity e WHERE e.paymentId = :paymentId")
    fun lastSequence(paymentId: String): Int

    fun findByPaymentIdOrderBySequence(paymentId: String): List<EventJpaEntity>
}

interface DeliveryJpaRepository : JpaRepository<DeliveryJpaEntity, Long> {
    fun countByEndpointAndStatus(
        endpoint: String,
        status: String,
    ): Long

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query(
        """
        SELECT d FROM DeliveryJpaEntity d
        WHERE d.status = 'pending' AND d.nextAttemptAt <= :now AND d.endpoint NOT IN :paused
          AND NOT EXISTS (SELECT 1 FROM EndpointStateJpaEntity s WHERE s.endpoint = d.endpoint AND s.disabledAt IS NOT NULL)
        ORDER BY d.nextAttemptAt
        """,
    )
    fun lockDue(
        now: Instant,
        paused: Collection<String>,
        limit: Limit,
    ): List<DeliveryJpaEntity>

    fun findByEventIdInOrderById(eventIds: Collection<String>): List<DeliveryJpaEntity>

    @Modifying
    @Query("UPDATE DeliveryJpaEntity d SET d.status = 'stopped', d.nextAttemptAt = NULL, d.updatedAt = :at WHERE d.endpoint = :endpoint AND d.status = 'pending'")
    fun stopPending(
        endpoint: String,
        at: Instant,
    ): Int

    fun countByStatusAndAttemptsGreaterThan(
        status: String,
        attempts: Int,
    ): Long
}

interface AttemptJpaRepository : JpaRepository<AttemptJpaEntity, Long> {
    @Query("SELECT COUNT(*) FROM (SELECT 1 FROM webhook_attempts LIMIT :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    fun findByDeliveryIdInOrderById(deliveryIds: Collection<Long>): List<AttemptJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<AttemptJpaEntity>
}

interface EndpointStateJpaRepository : JpaRepository<EndpointStateJpaEntity, String> {
    fun countByDisabledAtIsNotNull(): Long

    @Query(
        value = """
            INSERT INTO webhook_endpoint_state (endpoint, consecutive_abandoned) VALUES (:endpoint, 1)
            ON CONFLICT (endpoint) DO UPDATE SET consecutive_abandoned = webhook_endpoint_state.consecutive_abandoned + 1
            RETURNING consecutive_abandoned
        """,
        nativeQuery = true,
    )
    fun countAbandoned(endpoint: String): Int
}

interface SigningKeyJpaRepository : JpaRepository<SigningKeyJpaEntity, String> {
    @Query("SELECT pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    fun tryAdvisoryLock(key: Long): Boolean

    @Query("SELECT CAST(pg_advisory_xact_lock(:key) AS text)", nativeQuery = true)
    fun advisoryLock(key: Long): String
}

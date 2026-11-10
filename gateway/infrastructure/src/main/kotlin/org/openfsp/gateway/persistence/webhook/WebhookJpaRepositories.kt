package org.openfsp.gateway.persistence.webhook

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
    @Query("select coalesce(max(e.sequence), 0) from EventJpaEntity e where e.paymentId = :paymentId")
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
    @Query("select d from DeliveryJpaEntity d where d.status = 'pending' and d.nextAttemptAt <= :now order by d.nextAttemptAt")
    fun lockDue(
        now: Instant,
        limit: Limit,
    ): List<DeliveryJpaEntity>

    fun findByEventIdInOrderById(eventIds: Collection<String>): List<DeliveryJpaEntity>

    @Modifying
    @Query("update DeliveryJpaEntity d set d.status = 'stopped', d.nextAttemptAt = null, d.updatedAt = :at where d.endpoint = :endpoint and d.status = 'pending'")
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
    @Query("select count(*) from (select 1 from webhook_attempts limit :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    fun findByDeliveryIdInOrderById(deliveryIds: Collection<Long>): List<AttemptJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<AttemptJpaEntity>
}

interface EndpointStateJpaRepository : JpaRepository<EndpointStateJpaEntity, String> {
    fun countByDisabledAtIsNotNull(): Long
}

interface SigningKeyJpaRepository : JpaRepository<SigningKeyJpaEntity, String> {
    @Query("select pg_try_advisory_xact_lock(:key)", nativeQuery = true)
    fun tryAdvisoryLock(key: Long): Boolean
}

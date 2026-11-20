package org.openfsp.gateway.payments.persistence

import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface TransitionJpaRepository : JpaRepository<TransitionJpaEntity, Long> {
    fun findByPaymentIdOrderById(paymentId: String): List<TransitionJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<TransitionJpaEntity>

    fun findBySourceInOrderByIdDesc(
        sources: Collection<String>,
        limit: Limit,
    ): List<TransitionJpaEntity>

    @Query("SELECT COUNT(*) FROM (SELECT 1 FROM payment_transitions LIMIT :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    @Query("SELECT COUNT(*) FROM (SELECT 1 FROM payment_transitions WHERE source IN (:sources) LIMIT :cap) t", nativeQuery = true)
    fun countBySourceInUpTo(
        sources: Collection<String>,
        cap: Int,
    ): Long

    @Modifying
    @Query("DELETE FROM TransitionJpaEntity t WHERE t.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface ConflictJpaRepository : JpaRepository<ConflictJpaEntity, Long> {
    @Query("SELECT COUNT(*) FROM (SELECT 1 FROM payment_conflicts LIMIT :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    fun findByPaymentIdOrderById(paymentId: String): List<ConflictJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<ConflictJpaEntity>

    @Modifying
    @Query("DELETE FROM ConflictJpaEntity c WHERE c.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface AttestationJpaRepository : JpaRepository<AttestationJpaEntity, Long> {
    @Modifying
    @Query("DELETE FROM AttestationJpaEntity a WHERE a.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface IndeterminateOperationJpaRepository : JpaRepository<IndeterminateOperationJpaEntity, Long> {
    fun findByPaymentIdOrderById(paymentId: String): List<IndeterminateOperationJpaEntity>

    @Modifying
    @Query("DELETE FROM IndeterminateOperationJpaEntity o WHERE o.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

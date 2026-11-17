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

    @Query("select count(*) from (select 1 from payment_transitions limit :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    @Query("select count(*) from (select 1 from payment_transitions where source in (:sources) limit :cap) t", nativeQuery = true)
    fun countBySourceInUpTo(
        sources: Collection<String>,
        cap: Int,
    ): Long

    @Modifying
    @Query("delete from TransitionJpaEntity t where t.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface ConflictJpaRepository : JpaRepository<ConflictJpaEntity, Long> {
    @Query("select count(*) from (select 1 from payment_conflicts limit :cap) t", nativeQuery = true)
    fun countUpTo(cap: Int): Long

    fun findByPaymentIdOrderById(paymentId: String): List<ConflictJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<ConflictJpaEntity>

    @Modifying
    @Query("delete from ConflictJpaEntity c where c.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface AttestationJpaRepository : JpaRepository<AttestationJpaEntity, Long> {
    @Modifying
    @Query("delete from AttestationJpaEntity a where a.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface IndeterminateOperationJpaRepository : JpaRepository<IndeterminateOperationJpaEntity, Long> {
    fun findByPaymentIdOrderById(paymentId: String): List<IndeterminateOperationJpaEntity>

    @Modifying
    @Query("delete from IndeterminateOperationJpaEntity o where o.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

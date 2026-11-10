package org.openfsp.gateway.persistence.payment

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

    fun countBySourceIn(sources: Collection<String>): Long

    @Modifying
    @Query("delete from TransitionJpaEntity t where t.paymentId = :paymentId")
    fun deleteByPaymentId(paymentId: String)
}

interface ConflictJpaRepository : JpaRepository<ConflictJpaEntity, Long> {
    fun findByPaymentIdOrderById(paymentId: String): List<ConflictJpaEntity>

    fun findByOrderByIdDesc(limit: Limit): List<ConflictJpaEntity>
}

interface AttestationJpaRepository : JpaRepository<AttestationJpaEntity, Long>

interface IndeterminateOperationJpaRepository : JpaRepository<IndeterminateOperationJpaEntity, Long> {
    fun findByPaymentIdOrderById(paymentId: String): List<IndeterminateOperationJpaEntity>
}

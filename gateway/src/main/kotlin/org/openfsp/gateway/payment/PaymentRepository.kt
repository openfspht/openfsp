package org.openfsp.gateway.payment

import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query

interface PaymentRepository : JpaRepository<PaymentEntity, String> {
    fun findByReference(reference: String): PaymentEntity?

    fun existsByReference(reference: String): Boolean

    fun findByProviderAndProviderOrderId(
        provider: String,
        providerOrderId: String,
    ): PaymentEntity?

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    fun lockById(id: String): PaymentEntity?

    fun findTop100ByOrderByCreatedAtDesc(): List<PaymentEntity>
}

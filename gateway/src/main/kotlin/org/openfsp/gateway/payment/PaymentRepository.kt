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

    fun findByProviderAndCallbackTokenDigest(
        provider: String,
        callbackTokenDigest: ByteArray,
    ): PaymentEntity?

    fun findByProviderAndProviderReference(
        provider: String,
        providerReference: String,
    ): PaymentEntity?

    @Query("select p from PaymentEntity p where p.provider = :provider and p.status = :status and p.expiresAt < :before")
    fun findPendingExpiredBefore(
        provider: String,
        status: String,
        before: java.time.Instant,
    ): List<PaymentEntity>

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PaymentEntity p where p.id = :id")
    fun lockById(id: String): PaymentEntity?

    fun findTop100ByOrderByCreatedAtDesc(): List<PaymentEntity>

    @Query(
        value = "select * from payments where status = :status and updated_at < :before order by updated_at limit :batch",
        nativeQuery = true,
    )
    fun findPendingForReconciliation(
        status: String,
        before: java.time.Instant,
        batch: Int,
    ): List<PaymentEntity>
}

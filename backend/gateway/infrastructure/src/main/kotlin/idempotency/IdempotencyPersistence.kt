package org.openfsp.gateway.idempotency

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.io.Serializable
import java.time.Instant

@Embeddable
data class IdempotencyRecordId(
    var principal: String = "",
    var operation: String = "",
    var idempotencyKey: String = "",
) : Serializable

@Entity
@Table(name = "idempotency_records")
class IdempotencyRecordJpaEntity(
    @EmbeddedId var id: IdempotencyRecordId,
    var fingerprint: String,
    var responseStatus: Int?,
    var responseBody: ByteArray?,
    var createdAt: Instant,
    var claimedAt: Instant,
    var claimGeneration: Long,
)

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecordJpaEntity, IdempotencyRecordId> {
    @Modifying
    @Query(
        value = """
            INSERT INTO idempotency_records (principal, operation, idempotency_key, fingerprint, created_at, claimed_at)
            VALUES (:principal, :operation, :key, :fingerprint, :createdAt, :createdAt)
            ON CONFLICT DO NOTHING
        """,
        nativeQuery = true,
    )
    fun claim(
        principal: String,
        operation: String,
        key: String,
        fingerprint: String,
        createdAt: Instant,
    ): Int

    @Modifying
    @Query(
        """
            UPDATE IdempotencyRecordJpaEntity r SET r.claimedAt = :now, r.claimGeneration = r.claimGeneration + 1
            WHERE r.id = :id AND r.fingerprint = :fingerprint AND r.responseStatus IS NULL AND r.claimedAt < :expiredBefore AND r.claimGeneration = :generation
        """,
    )
    fun takeOver(
        id: IdempotencyRecordId,
        fingerprint: String,
        now: Instant,
        expiredBefore: Instant,
        generation: Long,
    ): Int

    @Modifying
    @Query(
        """
            UPDATE IdempotencyRecordJpaEntity r SET r.responseStatus = :status, r.responseBody = :body
            WHERE r.id = :id AND r.responseStatus IS NULL AND r.claimGeneration = :generation
        """,
    )
    fun complete(
        id: IdempotencyRecordId,
        generation: Long,
        status: Int,
        body: ByteArray,
    ): Int

    @Modifying
    @Query("DELETE FROM IdempotencyRecordJpaEntity r WHERE r.id = :id AND r.createdAt < :createdBefore")
    fun discardStale(
        id: IdempotencyRecordId,
        createdBefore: Instant,
    ): Int

    @Modifying
    @Query(
        value = """
            DELETE FROM idempotency_records WHERE (principal, operation, idempotency_key) IN (
                SELECT principal, operation, idempotency_key FROM idempotency_records
                WHERE created_at < :before ORDER BY created_at LIMIT :batch FOR UPDATE SKIP LOCKED
            )
        """,
        nativeQuery = true,
    )
    fun deleteBatchCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int

    @Modifying
    @Query("DELETE FROM IdempotencyRecordJpaEntity r WHERE r.id = :id AND r.responseStatus IS NULL AND r.claimGeneration = :generation")
    fun release(
        id: IdempotencyRecordId,
        generation: Long,
    ): Int
}

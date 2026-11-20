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
            insert into idempotency_records (principal, operation, idempotency_key, fingerprint, created_at, claimed_at)
            values (:principal, :operation, :key, :fingerprint, :createdAt, :createdAt)
            on conflict do nothing
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
            update IdempotencyRecordJpaEntity r set r.claimedAt = :now, r.claimGeneration = r.claimGeneration + 1
            where r.id = :id and r.fingerprint = :fingerprint and r.responseStatus is null and r.claimedAt < :expiredBefore and r.claimGeneration = :generation
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
            update IdempotencyRecordJpaEntity r set r.responseStatus = :status, r.responseBody = :body
            where r.id = :id and r.responseStatus is null and r.claimGeneration = :generation
        """,
    )
    fun complete(
        id: IdempotencyRecordId,
        generation: Long,
        status: Int,
        body: ByteArray,
    ): Int

    @Modifying
    @Query("delete from IdempotencyRecordJpaEntity r where r.id = :id and r.createdAt < :createdBefore")
    fun discardStale(
        id: IdempotencyRecordId,
        createdBefore: Instant,
    ): Int

    @Modifying
    @Query(
        value = """
            delete from idempotency_records where (principal, operation, idempotency_key) in (
                select principal, operation, idempotency_key from idempotency_records
                where created_at < :before order by created_at limit :batch for update skip locked
            )
        """,
        nativeQuery = true,
    )
    fun deleteBatchCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int

    @Modifying
    @Query("delete from IdempotencyRecordJpaEntity r where r.id = :id and r.responseStatus is null and r.claimGeneration = :generation")
    fun release(
        id: IdempotencyRecordId,
        generation: Long,
    ): Int
}

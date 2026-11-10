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
)

interface IdempotencyRecordRepository : JpaRepository<IdempotencyRecordJpaEntity, IdempotencyRecordId> {
    @Modifying
    @Query(
        value = """
            insert into idempotency_records (principal, operation, idempotency_key, fingerprint, created_at)
            values (:principal, :operation, :key, :fingerprint, :createdAt)
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
    @Query("delete from IdempotencyRecordJpaEntity r where r.createdAt < :before")
    fun deleteCreatedBefore(before: Instant): Int

    @Modifying
    @Query("delete from IdempotencyRecordJpaEntity r where r.id = :id and r.responseStatus is null")
    fun release(id: IdempotencyRecordId): Int
}

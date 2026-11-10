package org.openfsp.gateway.auditing.persistence

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import java.time.Instant

@Entity
@Table(name = "audit_records")
class AuditRecordJpaEntity(
    val recordedAt: Instant,
    val kind: String,
    val principalId: String?,
    val lookupPrefix: String?,
    val operation: String,
    val requestId: String?,
    val result: String,
    val detail: String?,
    val remoteAddress: String? = null,
    val durationMs: Long? = null,
    @JdbcTypeCode(SqlTypes.JSON) val requestHeaders: Map<String, String>? = null,
    val requestBody: String? = null,
    @JdbcTypeCode(SqlTypes.JSON) val responseHeaders: Map<String, String>? = null,
    val responseBody: String? = null,
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

interface AuditRecordJpaRepository :
    JpaRepository<AuditRecordJpaEntity, Long>,
    JpaSpecificationExecutor<AuditRecordJpaEntity> {
    fun findByKindInOrderByIdDesc(
        kinds: Collection<String>,
        limit: Limit,
    ): List<AuditRecordJpaEntity>

    @Query("select count(*) from (select 1 from audit_records where kind in (:kinds) limit :cap) t", nativeQuery = true)
    fun countByKindInUpTo(
        kinds: Collection<String>,
        cap: Int,
    ): Long
}

@Component
class JpaAuditTrail(
    private val repository: AuditRecordJpaRepository,
) : AuditTrail {
    override fun record(record: AuditRecord) {
        repository.save(
            AuditRecordJpaEntity(
                recordedAt = record.at,
                kind = record.kind.wire,
                principalId = record.principalId,
                lookupPrefix = record.lookupPrefix,
                operation = record.operation,
                requestId = record.requestId,
                result = record.result,
                detail = record.detail,
                remoteAddress = record.exchange?.remoteAddress,
                durationMs = record.exchange?.durationMs,
                requestHeaders = record.exchange?.requestHeaders,
                requestBody = record.exchange?.requestBody,
                responseHeaders = record.exchange?.responseHeaders,
                responseBody = record.exchange?.responseBody,
            ),
        )
    }
}

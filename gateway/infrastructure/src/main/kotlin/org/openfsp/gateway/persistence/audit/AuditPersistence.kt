package org.openfsp.gateway.persistence.audit

import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.repository.JpaRepository
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
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long? = null,
)

interface AuditRecordJpaRepository : JpaRepository<AuditRecordJpaEntity, Long> {
    fun findByOrderByIdDesc(limit: Limit): List<AuditRecordJpaEntity>

    fun findByKindInOrderByIdDesc(
        kinds: Collection<String>,
        limit: Limit,
    ): List<AuditRecordJpaEntity>
}

@Component
class JpaAuditTrail(
    private val repository: AuditRecordJpaRepository,
) : AuditTrail {
    override fun record(record: AuditRecord) {
        repository.save(
            AuditRecordJpaEntity(record.at, record.kind.wire, record.principalId, record.lookupPrefix, record.operation, record.requestId, record.result, record.detail),
        )
    }

    override fun latest(limit: Int): List<AuditRecord> =
        repository.findByOrderByIdDesc(Limit.of(limit)).map {
            AuditRecord(it.recordedAt, AuditKind.entries.first { kind -> kind.wire == it.kind }, it.principalId, it.lookupPrefix, it.operation, it.requestId, it.result, it.detail)
        }
}

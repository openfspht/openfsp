package org.openfsp.gateway.admin.search

import org.openfsp.gateway.persistence.audit.AuditRecordJpaEntity
import org.openfsp.gateway.persistence.audit.AuditRecordJpaRepository
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class AuditRecordResource(
    val recordedAt: Instant,
    val kind: String,
    val principalId: String?,
    val lookupPrefix: String?,
    val operation: String,
    val requestId: String?,
    val result: String,
    val detail: String?,
)

data class AuditFilters(
    val kind: String?,
    val query: String?,
)

@Component
class AuditSearch(
    private val repository: AuditRecordJpaRepository,
) {
    @Transactional(readOnly = true)
    fun search(
        filters: AuditFilters,
        page: PageQuery,
    ): PageResource<AuditRecordResource> =
        repository
            .findAll(specificationOf(filters), page.toPageable(Sort.by(Sort.Direction.DESC, ID)))
            .toResource { AuditRecordResource(it.recordedAt, it.kind, it.principalId, it.lookupPrefix, it.operation, it.requestId, it.result, it.detail) }

    private fun specificationOf(filters: AuditFilters): Specification<AuditRecordJpaEntity> =
        Specification { root, _, builder ->
            val conditions =
                listOfNotNull(
                    filters.kind?.let { builder.equal(root.get<String>("kind"), it) },
                    filters.query?.lowercase()?.let { needle ->
                        val pattern = "%$needle%"
                        builder.or(
                            builder.like(builder.lower(root.get("operation")), pattern),
                            builder.like(builder.lower(builder.coalesce(root.get("lookupPrefix"), "")), pattern),
                            builder.like(builder.lower(root.get("result")), pattern),
                        )
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private companion object {
        const val ID = "id"
    }
}

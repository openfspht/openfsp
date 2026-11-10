package org.openfsp.gateway.admin.search

import org.openfsp.gateway.audit.HttpExchange
import org.openfsp.gateway.persistence.audit.AuditRecordJpaEntity
import org.openfsp.gateway.persistence.audit.AuditRecordJpaRepository
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
    val exchange: HttpExchange?,
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
        sort: SortQuery,
    ): PageResource<AuditRecordResource> =
        repository
            .findAll(specificationOf(filters), page.toPageable(sort.resolve(SORTABLE, RECORDED)))
            .toResource { AuditRecordResource(it.recordedAt, it.kind, it.principalId, it.lookupPrefix, it.operation, it.requestId, it.result, it.detail, exchangeOf(it)) }

    private fun specificationOf(filters: AuditFilters): Specification<AuditRecordJpaEntity> =
        Specification { root, _, builder ->
            val conditions =
                listOfNotNull(
                    filters.kind?.let { builder.equal(root.get<String>("kind"), it) },
                    filters.query?.let { needle ->
                        builder.or(
                            builder.contains(root.get("operation"), needle),
                            builder.contains(builder.coalesce(root.get("lookupPrefix"), ""), needle),
                            builder.contains(root.get("result"), needle),
                        )
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private fun exchangeOf(entity: AuditRecordJpaEntity): HttpExchange? =
        entity.remoteAddress?.let {
            HttpExchange(it, entity.durationMs ?: 0, entity.requestHeaders.orEmpty(), entity.requestBody.orEmpty(), entity.responseHeaders.orEmpty(), entity.responseBody.orEmpty())
        }

    private companion object {
        const val RECORDED = "recorded_at"
        val SORTABLE = mapOf(RECORDED to "recordedAt", "kind" to "kind", "operation" to "operation", "principal" to "lookupPrefix", "result" to "result")
    }
}

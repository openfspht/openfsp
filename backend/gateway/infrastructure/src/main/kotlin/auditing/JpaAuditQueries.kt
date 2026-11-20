package org.openfsp.gateway.auditing

import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.audit.HttpExchange
import org.openfsp.gateway.audit.spi.AuditQueries
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.shared.persistence.contains
import org.openfsp.gateway.shared.persistence.toPage
import org.openfsp.gateway.shared.persistence.toPageable
import org.openfsp.gateway.shared.persistence.toSort
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component

@Component
class JpaAuditQueries(
    private val repository: AuditRecordJpaRepository,
) : AuditQueries {
    override fun search(
        filters: AuditFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<AuditEntry> = repository.findAll(specificationOf(filters), page.toPageable(sort.toSort(SORTABLE, RECORDED))).toPage(::entry)

    override fun latest(
        kinds: Set<String>,
        limit: Int,
    ): List<AuditEntry> = repository.findByKindInOrderByIdDesc(kinds, Limit.of(limit)).map(::entry)

    override fun countUpTo(
        kinds: Set<String>,
        cap: Int,
    ): Long = repository.countByKindInUpTo(kinds, cap)

    private fun entry(entity: AuditRecordJpaEntity) =
        AuditEntry(entity.id!!, entity.recordedAt, entity.kind, entity.principalId, entity.lookupPrefix, entity.operation, entity.requestId, entity.result, entity.detail, exchangeOf(entity))

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

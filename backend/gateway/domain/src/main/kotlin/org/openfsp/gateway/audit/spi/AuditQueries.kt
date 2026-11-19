package org.openfsp.gateway.audit.spi

import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery

interface AuditQueries {
    fun search(
        filters: AuditFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<AuditEntry>

    fun latest(
        kinds: Set<String>,
        limit: Int,
    ): List<AuditEntry>

    fun countUpTo(
        kinds: Set<String>,
        cap: Int,
    ): Long
}

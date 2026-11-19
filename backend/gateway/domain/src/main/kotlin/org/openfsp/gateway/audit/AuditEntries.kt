package org.openfsp.gateway.audit

import java.time.Instant

data class AuditFilters(
    val kind: String?,
    val query: String?,
)

data class AuditEntry(
    val id: Long,
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

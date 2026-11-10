package org.openfsp.gateway.audit

import java.time.Instant

enum class AuditKind(
    val wire: String,
) {
    REQUEST("request"),
    AUTHENTICATION_FAILURE("authentication_failure"),
    KEY_ISSUED("key_issued"),
    KEY_REVOKED("key_revoked"),
}

data class AuditRecord(
    val at: Instant,
    val kind: AuditKind,
    val principalId: String?,
    val lookupPrefix: String?,
    val operation: String,
    val requestId: String?,
    val result: String,
    val detail: String? = null,
)

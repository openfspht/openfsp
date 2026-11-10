package org.openfsp.gateway.audit

import java.time.Instant

enum class AuditKind(
    val wire: String,
) {
    REQUEST("request"),
    AUTHENTICATION_FAILURE("authentication_failure"),
    KEY_ISSUED("key_issued"),
    KEY_REVOKED("key_revoked"),
    ADMIN_SIGN_IN("admin_sign_in"),
    ADMIN_SIGN_IN_FAILURE("admin_sign_in_failure"),
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

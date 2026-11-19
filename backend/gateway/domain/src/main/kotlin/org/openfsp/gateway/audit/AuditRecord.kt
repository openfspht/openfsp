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
    WEBHOOK_ADDED("webhook_added"),
    WEBHOOK_REMOVED("webhook_removed"),
    ACCOUNT_OPENED("account_opened"),
    PROVIDER_CREDENTIALS_SET("provider_credentials_set"),
    PROVIDER_CREDENTIALS_REMOVED("provider_credentials_removed"),
    PROVIDER_CREDENTIALS_REVOKED("provider_credentials_revoked"),
    PROVIDER_CREDENTIAL_MODE_CHANGED("provider_credential_mode_changed"),
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
    val exchange: HttpExchange? = null,
)

data class HttpExchange(
    val remoteAddress: String,
    val durationMs: Long,
    val requestHeaders: Map<String, String>,
    val requestBody: String,
    val responseHeaders: Map<String, String>,
    val responseBody: String,
)

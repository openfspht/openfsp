package org.openfsp.gateway.auth

import java.time.Instant

enum class Scope(
    val wire: String,
) {
    CAPABILITIES_READ("capabilities:read"),
    PAYMENTS_READ("payments:read"),
    PAYMENTS_WRITE("payments:write"),
    STATEMENTS_READ("statements:read"),
    ;

    companion object {
        fun fromWire(value: String): Scope? = entries.firstOrNull { it.wire == value }
    }
}

data class ApiKey(
    val id: String,
    val principalId: String,
    val lookupPrefix: String,
    val digest: String,
    val scopes: Set<Scope>,
    val environment: String,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
) {
    fun usableAt(
        instant: Instant,
        environment: String,
    ): Boolean = this.environment == environment && revokedAt == null && (expiresAt == null || expiresAt.isAfter(instant))
}

data class AuthenticatedKey(
    val keyId: String,
    val principalId: String,
    val lookupPrefix: String,
    val scopes: Set<Scope>,
)

data class IssuedKey(
    val keyId: String,
    val secret: String,
)

data class KeySummary(
    val id: String,
    val principal: String,
    val prefix: String,
    val scopes: Set<Scope>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
)

data class PresentedKey(
    val environment: String,
    val lookupPrefix: String,
) {
    companion object {
        private val FORMAT = Regex("^ofsp_(live|test)_([A-Za-z0-9]{34,})$")
        private const val LOOKUP_PREFIX = 12

        fun parse(value: String): PresentedKey? = FORMAT.matchEntire(value)?.let { PresentedKey(it.groupValues[1], it.groupValues[2].take(LOOKUP_PREFIX)) }
    }
}

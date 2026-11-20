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

        val ALWAYS_GRANTED: Set<Scope> = setOf(CAPABILITIES_READ)
    }
}

data class ApiKey(
    val id: String,
    val principalId: String,
    val accountId: String,
    val lookupPrefix: String,
    val digest: String,
    val scopes: Set<Scope>,
    val environment: String,
    val createdAt: Instant,
    val expiresAt: Instant? = null,
    val revokedAt: Instant? = null,
    val demo: Boolean = false,
) {
    fun usableAt(
        instant: Instant,
        environment: GatewayEnvironment,
    ): Boolean = this.environment == environment.name && (!demo || environment.demo) && revokedAt == null && (expiresAt == null || expiresAt.isAfter(instant))
}

data class AuthenticatedKey(
    val keyId: String,
    val principalId: String,
    val accountId: String,
    val lookupPrefix: String,
    val scopes: Set<Scope>,
)

data class IssuedKey(
    val keyId: String,
    val secret: String,
    val principalId: String,
    val accountId: String,
)

data class PresentedKey(
    val environment: String,
    val lookupPrefix: String,
) {
    companion object {
        const val SCHEME = "ofsp_"
        private val FORMAT = Regex("^$SCHEME(live|test)_([A-Za-z0-9]{34,})$")
        private const val LOOKUP_PREFIX = 12

        fun parse(value: String): PresentedKey? = FORMAT.matchEntire(value)?.let { PresentedKey(it.groupValues[1], it.groupValues[2].take(LOOKUP_PREFIX)) }

        fun prefixOf(environment: String): String = "$SCHEME${environment}_"

        fun display(
            environment: String,
            lookupPrefix: String,
        ): String = prefixOf(environment) + lookupPrefix

        fun redact(value: String): String = parse(value)?.let { display(it.environment, it.lookupPrefix) } ?: MALFORMED

        private const val MALFORMED = "malformed"
    }
}

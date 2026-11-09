package org.openfsp.gateway.auth

import org.openfsp.gateway.GatewayProperties
import org.openfsp.protocol.ResourceIds
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Clock
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

data class AuthenticatedKey(
    val keyId: String,
    val principalId: String,
    val scopes: Set<Scope>,
)

data class IssuedKey(
    val keyId: String,
    val secret: String,
)

@Component
class ApiKeys(
    private val jdbc: JdbcClient,
    private val properties: GatewayProperties,
    private val clock: Clock,
) {
    private data class Candidate(
        val keyId: String,
        val principalId: String,
        val digest: ByteArray,
        val scopes: String,
        val expiresAt: Instant?,
        val revokedAt: Instant?,
    )

    private val random = SecureRandom()
    private val environment = properties.environment.name.lowercase()

    fun authenticate(presented: String): AuthenticatedKey? {
        val match = FORMAT.matchEntire(presented)
        val prefix = match?.groupValues?.get(2)?.take(LOOKUP_PREFIX)
        val digest = digest(presented)
        val candidates =
            if (prefix == null) {
                emptyList()
            } else {
                jdbc
                    .sql("select id, principal_id, digest, scopes, expires_at, revoked_at from api_keys where lookup_prefix = ?")
                    .param(prefix)
                    .query { rs, _ ->
                        Candidate(
                            rs.getString(1),
                            rs.getString(2),
                            rs.getBytes(3),
                            rs.getString(4),
                            rs.getTimestamp(5)?.toInstant(),
                            rs.getTimestamp(6)?.toInstant(),
                        )
                    }.list()
            }
        var found: Candidate? = null
        for (candidate in candidates.ifEmpty { listOf(DUMMY) }) {
            if (MessageDigest.isEqual(candidate.digest, digest) && candidate !== DUMMY) found = candidate
        }
        val now = clock.instant()
        val valid =
            found?.takeIf {
                match?.groupValues?.get(1) == environment &&
                    it.revokedAt == null &&
                    (it.expiresAt == null || it.expiresAt.isAfter(now))
            } ?: return null
        jdbc.sql("update api_keys set last_used_at = ? where id = ?").params(Timestamp.from(now), valid.keyId).update()
        return AuthenticatedKey(valid.keyId, valid.principalId, valid.scopes.split(' ').mapNotNull(Scope::fromWire).toSet())
    }

    fun register(
        principalName: String,
        key: String,
        scopes: Set<Scope>,
    ): String {
        val match = FORMAT.matchEntire(key)
        require(match != null && match.groupValues[1] == environment) { "the key does not follow ofsp_${environment}_<secret>" }
        val existing =
            jdbc.sql("select id from api_keys where digest = ?").param(digest(key)).query(String::class.java).optional().orElse(null)
        if (existing != null) return existing
        val now = Timestamp.from(clock.instant())
        val principalId = principal(principalName, now)
        val keyId = ResourceIds.generate("key", clock)
        jdbc
            .sql(
                "insert into api_keys (id, principal_id, lookup_prefix, digest, scopes, environment, created_at) values (?, ?, ?, ?, ?, ?, ?)",
            ).params(keyId, principalId, match.groupValues[2].take(LOOKUP_PREFIX), digest(key), scopes.joinToString(" ") { it.wire }, environment, now)
            .update()
        return keyId
    }

    fun issue(
        principalName: String,
        scopes: Set<Scope>,
    ): IssuedKey {
        val now = Timestamp.from(clock.instant())
        val principalId = principal(principalName, now)
        val secret = (1..SECRET_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        val key = "ofsp_${environment}_$secret"
        val keyId = ResourceIds.generate("key", clock)
        jdbc
            .sql(
                "insert into api_keys (id, principal_id, lookup_prefix, digest, scopes, environment, created_at) values (?, ?, ?, ?, ?, ?, ?)",
            ).params(keyId, principalId, secret.take(LOOKUP_PREFIX), digest(key), scopes.joinToString(" ") { it.wire }, environment, now)
            .update()
        return IssuedKey(keyId, key)
    }

    private fun principal(
        name: String,
        now: Timestamp,
    ): String =
        jdbc
            .sql("select id from principals where name = ?")
            .param(name)
            .query(String::class.java)
            .optional()
            .orElseGet {
                ResourceIds.generate("prn", clock).also { id ->
                    jdbc.sql("insert into principals (id, name, created_at) values (?, ?, ?)").params(id, name, now).update()
                }
            }

    fun revoke(keyId: String) {
        jdbc
            .sql("update api_keys set revoked_at = ?, digest = ? where id = ? and revoked_at is null")
            .params(Timestamp.from(clock.instant()), ByteArray(DIGEST_BYTES), keyId)
            .update()
    }

    fun isEmpty(): Boolean = jdbc.sql("select count(*) from api_keys").query(Long::class.java).single() == 0L

    private fun digest(key: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))

    private companion object {
        val FORMAT = Regex("^ofsp_(live|test)_([A-Za-z0-9]{34,})$")
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val SECRET_LENGTH = 40
        const val LOOKUP_PREFIX = 12
        const val DIGEST_BYTES = 32
        val DUMMY = Candidate("", "", ByteArray(DIGEST_BYTES), "", null, null)
    }
}

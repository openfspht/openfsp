package org.openfsp.gateway.idempotency

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.util.HexFormat

data class IdempotencyScope(
    val principal: String,
    val operation: String,
    val key: String,
)

sealed interface Claim {
    data object Acquired : Claim

    data class Replay(
        val status: Int,
        val body: ByteArray,
    ) : Claim

    data object InProgress : Claim

    data object KeyReused : Claim
}

@Component
class Idempotency(
    private val jdbc: JdbcClient,
    private val json: JsonMapper,
    private val clock: Clock,
) {
    fun fingerprint(body: ByteArray): String {
        val tree = if (body.isEmpty()) json.createObjectNode() else json.readTree(body)
        val canonical = json.writeValueAsBytes(canonicalize(tree))
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical))
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun claim(
        scope: IdempotencyScope,
        fingerprint: String,
    ): Claim {
        jdbc
            .sql("delete from idempotency_records where created_at < :cutoff")
            .param("cutoff", java.sql.Timestamp.from(clock.instant().minus(RETENTION)))
            .update()
        val inserted =
            jdbc
                .sql(
                    """
                    insert into idempotency_records (principal, operation, idempotency_key, fingerprint, created_at)
                    values (:principal, :operation, :key, :fingerprint, :createdAt)
                    on conflict do nothing
                    """.trimIndent(),
                ).param("principal", scope.principal)
                .param("operation", scope.operation)
                .param("key", scope.key)
                .param("fingerprint", fingerprint)
                .param("createdAt", java.sql.Timestamp.from(clock.instant()))
                .update()
        if (inserted == 1) return Claim.Acquired

        val existing =
            jdbc
                .sql(
                    "select fingerprint, response_status, response_body from idempotency_records " +
                        "where principal = :principal and operation = :operation and idempotency_key = :key",
                ).param("principal", scope.principal)
                .param("operation", scope.operation)
                .param("key", scope.key)
                .query { rs, _ -> Triple(rs.getString(1), rs.getObject(2) as Int?, rs.getBytes(3)) }
                .optional()
                .orElse(null) ?: return Claim.InProgress
        val (storedFingerprint, status, responseBody) = existing
        return when {
            storedFingerprint != fingerprint -> Claim.KeyReused
            status == null -> Claim.InProgress
            else -> Claim.Replay(status, responseBody)
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun complete(
        scope: IdempotencyScope,
        status: Int,
        body: ByteArray,
    ) {
        jdbc
            .sql(
                "update idempotency_records set response_status = :status, response_body = :body " +
                    "where principal = :principal and operation = :operation and idempotency_key = :key and response_status is null",
            ).param("status", status)
            .param("body", body)
            .param("principal", scope.principal)
            .param("operation", scope.operation)
            .param("key", scope.key)
            .update()
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun release(scope: IdempotencyScope) {
        jdbc
            .sql(
                "delete from idempotency_records " +
                    "where principal = :principal and operation = :operation and idempotency_key = :key and response_status is null",
            ).param("principal", scope.principal)
            .param("operation", scope.operation)
            .param("key", scope.key)
            .update()
    }

    private fun canonicalize(node: JsonNode): JsonNode =
        when (node) {
            is ObjectNode ->
                json.createObjectNode().also { sorted ->
                    node.propertyNames().sorted().forEach { name -> sorted.set(name, canonicalize(node.get(name))) }
                }
            else ->
                if (node.isArray) {
                    json.createArrayNode().also { array -> node.forEach { array.add(canonicalize(it)) } }
                } else {
                    node
                }
        }

    companion object {
        val RETENTION: Duration = Duration.ofDays(7)
        const val RETRY_AFTER_SECONDS = "1"
    }
}

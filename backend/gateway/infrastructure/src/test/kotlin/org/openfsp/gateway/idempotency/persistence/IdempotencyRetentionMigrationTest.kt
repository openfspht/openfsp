package org.openfsp.gateway.idempotency.persistence

import org.flywaydb.core.Flyway
import org.openfsp.gateway.idempotency.IdempotentExecutions
import org.openfsp.gateway.providers.credentials.CredentialCipher
import org.openfsp.gateway.shared.security.ApplicationKey
import org.openfsp.gateway.webhooks.persistence.SealWebhookSigningKeys
import org.openfsp.gateway.webhooks.persistence.SigningKeySeal
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class IdempotencyRetentionMigrationTest {
    private val seal = SigningKeySeal(CredentialCipher(ApplicationKey(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))).derive(ApplicationKey.Purpose.WEBHOOK_SIGNING_KEYS)))

    @Test
    fun `the migration deletes the idempotency records past the retention and keeps the recent ones`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("32").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            val now = Instant.now()
            mapOf("expired" to now.minus(IdempotentExecutions.RETENTION).minus(Duration.ofHours(1)), "recent" to now.minus(Duration.ofDays(1))).forEach { (key, createdAt) ->
                jdbc
                    .sql("insert into idempotency_records (principal, operation, idempotency_key, fingerprint, fingerprint_version, created_at, claimed_at) values ('p', 'payments.create', ?, 'f', 1, ?, ?)")
                    .params(key, Timestamp.from(createdAt), Timestamp.from(createdAt))
                    .update()
            }

            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("33").load().migrate()

            assertEquals(listOf("recent"), jdbc.sql("select idempotency_key from idempotency_records").query(String::class.java).list())
        }
    }
}

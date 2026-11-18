package org.openfsp.gateway.webhooks.persistence

import org.flywaydb.core.Flyway
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.openfsp.gateway.shared.security.ApplicationKey
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.sql.Timestamp
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SealWebhookSigningKeysTest {
    private val seal = SigningKeySeal(ApplicationKey(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))))

    @Test
    fun `the migration seals every stored private key under its kid and drops the plaintext column`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).target("26").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            val stored =
                listOf("whk_first", "whk_second").associateWith { kid ->
                    val pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
                    jdbc
                        .sql("insert into webhook_signing_keys (kid, private_key, public_key, created_at, activates_at) values (?, ?, ?, ?, ?)")
                        .params(kid, pair.private.encoded, pair.public.encoded, Timestamp.from(Instant.now()), Timestamp.from(Instant.now()))
                        .update()
                    pair.private.encoded
                }

            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("27").load().migrate()

            val columns = jdbc.sql("select column_name from information_schema.columns where table_name = 'webhook_signing_keys'").query(String::class.java).list()
            assertFalse("private_key" in columns)
            stored.forEach { (kid, plaintext) ->
                val row = jdbc.sql("select sealing_key_id, private_key_nonce, private_key_ciphertext from webhook_signing_keys where kid = ?").param(kid).query().singleRow()
                val sealed = SealedCredentials(row["sealing_key_id"] as String, row["private_key_nonce"] as ByteArray, row["private_key_ciphertext"] as ByteArray)
                assertFalse(sealed.ciphertext.contentEquals(plaintext))
                assertContentEquals(plaintext, seal.open(kid, sealed))
                val other = stored.keys.first { it != kid }
                assertTrue(runCatching { seal.open(other, sealed) }.isFailure)
            }
            assertEquals("27", jdbc.sql("select max(version::int)::text from flyway_schema_history").query(String::class.java).single())
        }
    }
}

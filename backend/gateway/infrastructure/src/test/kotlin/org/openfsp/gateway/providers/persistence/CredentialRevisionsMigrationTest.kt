package org.openfsp.gateway.providers.persistence

import org.flywaydb.core.Flyway
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
import java.time.Instant
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CredentialRevisionsMigrationTest {
    private val seal = SigningKeySeal(CredentialCipher(ApplicationKey(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes))).derive(ApplicationKey.Purpose.WEBHOOK_SIGNING_KEYS)))

    @Test
    fun `the stored credentials become active revisions that the payments of their account reference`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("33").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            val updatedAt = Timestamp.from(Instant.parse("2026-11-01T10:00:00Z"))
            jdbc.sql("insert into merchant_accounts (id, name, created_at) values ('acct_shop', 'shop', now())").update()
            jdbc
                .sql("insert into provider_credentials (account_id, provider, key_id, nonce, ciphertext, names, updated_at) values ('acct_shop', 'moncash', 'k1', ?, ?, 'client-id client-secret', ?)")
                .params(byteArrayOf(1), byteArrayOf(2, 3), updatedAt)
                .update()
            listOf(
                Triple("pay_own", "moncash", "account"),
                Triple("pay_orphan", "natcash", "account"),
                Triple("pay_platform", "moncash", "platform"),
            ).forEach { (id, provider, credentials) ->
                jdbc
                    .sql(
                        """
                        insert into payments (id, account_id, reference, status, amount, currency, provider, provider_order_id, created_at, updated_at, provider_credentials)
                        values (?, 'acct_shop', ?, 'pending', 1000, 'HTG', ?, ?, now(), now(), ?)
                        """,
                    ).params(id, id, provider, "order-$id", credentials)
                    .update()
            }

            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("34").load().migrate()

            val active = jdbc.sql("select * from provider_credential_revisions where provider = 'moncash'").query().singleRow()
            assertEquals("active", active["status"])
            assertEquals("k1", active["key_id"])
            assertContentEquals(byteArrayOf(2, 3), active["ciphertext"] as ByteArray)
            assertEquals("client-id client-secret", active["names"])
            assertEquals(updatedAt.toInstant(), (active["created_at"] as Timestamp).toInstant())
            val orphan = jdbc.sql("select * from provider_credential_revisions where provider = 'natcash'").query().singleRow()
            assertEquals("retired", orphan["status"])
            assertNull(orphan["ciphertext"])
            val revisionOf = { payment: String -> jdbc.sql("select credential_revision from payments where id = ?").param(payment).query(String::class.java).optional().orElse(null) }
            assertEquals(active["id"], revisionOf("pay_own"))
            assertEquals(orphan["id"], revisionOf("pay_orphan"))
            assertNull(revisionOf("pay_platform"))
            assertEquals(0, jdbc.sql("select count(*) from information_schema.tables where table_name = 'provider_credentials'").query(Int::class.java).single())
        }
    }

    @Test
    fun `an account that has or had its own revisions uses its own credentials, every other pair the platform`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("34").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            jdbc.sql("insert into merchant_accounts (id, name, created_at) values ('acct_shop', 'shop', now()), ('acct_other', 'other', now())").update()
            jdbc
                .sql(
                    """
                    insert into provider_credential_revisions (id, account_id, provider, status, names, key_id, nonce, ciphertext, created_at, retired_at) values
                    ('cred_active', 'acct_shop', 'moncash', 'active', 'client-id', 'k1', decode('01', 'hex'), decode('02', 'hex'), now(), null),
                    ('cred_retired', 'acct_shop', 'moncash', 'retired', 'client-id', 'k1', decode('01', 'hex'), decode('02', 'hex'), now(), now()),
                    ('cred_removed', 'acct_shop', 'natcash', 'retired', 'username', null, null, null, now(), now())
                    """,
                ).update()

            Flyway.configure().dataSource(dataSource).javaMigrations(SealWebhookSigningKeys(seal)).target("35").load().migrate()

            val modes = jdbc.sql("select account_id || ' ' || provider || ' ' || mode from provider_credential_modes order by 1").query(String::class.java).list()
            assertEquals(listOf("acct_shop moncash own", "acct_shop natcash own"), modes)
        }
    }
}

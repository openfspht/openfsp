package org.openfsp.gateway.providers.persistence

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.configuration.Configuration
import org.flywaydb.core.api.migration.Context
import org.openfsp.gateway.providers.credentials.CredentialCipher
import org.openfsp.gateway.providers.credentials.ProviderCredentialSeal
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.openfsp.gateway.shared.security.ApplicationKey
import org.openfsp.gateway.webhooks.persistence.SealWebhookSigningKeys
import org.openfsp.gateway.webhooks.persistence.SigningKeySeal
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.security.SecureRandom
import java.sql.Connection
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith

class ProviderCredentialsBindingMigrationTest {
    private val applicationKey = ApplicationKey(Base64.getEncoder().encodeToString(ByteArray(32).also(SecureRandom()::nextBytes)))
    private val cipher = CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.PROVIDER_CREDENTIALS))
    private val seal = ProviderCredentialSeal(cipher)
    private val signingKeys = SealWebhookSigningKeys(SigningKeySeal(CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.WEBHOOK_SIGNING_KEYS))))
    private val migration = BindProviderCredentialsToRevisions(seal)
    private val plaintext = """{"client-id":"a","client-secret":"b"}""".toByteArray()

    @Test
    fun `secrets sealed for their account and provider are sealed again for their revision, once`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).javaMigrations(signingKeys).target("35").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            jdbc.sql("insert into merchant_accounts (id, name, created_at) values ('acct_shop', 'shop', now())").update()
            val unbound = cipher.seal(plaintext, "openfsp-provider-credentials\u0000acct_shop\u0000moncash")
            insert(jdbc, "cred_unbound", "active", unbound)
            val foreign = SealedCredentials("sealedbyoldkey00", byteArrayOf(1), byteArrayOf(2))
            insert(jdbc, "cred_foreign", "retired", foreign)

            Flyway.configure().dataSource(dataSource).javaMigrations(signingKeys, migration).target("36").load().migrate()

            assertContentEquals(plaintext, seal.open("acct_shop", "moncash", "cred_unbound", sealedOf(jdbc, "cred_unbound")))
            assertFailsWith<AEADBadTagException> { seal.openUnboundToRevision("acct_shop", "moncash", sealedOf(jdbc, "cred_unbound")) }
            assertContentEquals(foreign.ciphertext, sealedOf(jdbc, "cred_foreign").ciphertext)

            dataSource.connection.use { migration.migrate(contextOf(it)) }
            assertContentEquals(plaintext, seal.open("acct_shop", "moncash", "cred_unbound", sealedOf(jdbc, "cred_unbound")))
        }
    }

    @Test
    fun `a secret that opens neither way stops the migration`() {
        PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine")).use { postgres ->
            postgres.start()
            val dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway.configure().dataSource(dataSource).javaMigrations(signingKeys).target("35").load().migrate()
            val jdbc = JdbcClient.create(dataSource)
            jdbc.sql("insert into merchant_accounts (id, name, created_at) values ('acct_shop', 'shop', now())").update()
            insert(jdbc, "cred_tampered", "active", cipher.seal(plaintext, "openfsp-provider-credentials\u0000acct_other\u0000moncash"))

            assertFailsWith<Exception> { Flyway.configure().dataSource(dataSource).javaMigrations(signingKeys, migration).target("36").load().migrate() }
        }
    }

    private fun insert(
        jdbc: JdbcClient,
        id: String,
        status: String,
        sealed: SealedCredentials,
    ) {
        jdbc
            .sql(
                """
                insert into provider_credential_revisions (id, account_id, provider, status, names, key_id, nonce, ciphertext, created_at, retired_at)
                values (?, 'acct_shop', 'moncash', ?, 'client-id client-secret', ?, ?, ?, now(), case when ? = 'retired' then now() end)
                """,
            ).params(id, status, sealed.keyId, sealed.nonce, sealed.ciphertext, status)
            .update()
    }

    private fun sealedOf(
        jdbc: JdbcClient,
        id: String,
    ): SealedCredentials {
        val row = jdbc.sql("select key_id, nonce, ciphertext from provider_credential_revisions where id = ?").param(id).query().singleRow()
        return SealedCredentials(row["key_id"] as String, row["nonce"] as ByteArray, row["ciphertext"] as ByteArray)
    }

    private fun contextOf(connection: Connection) =
        object : Context {
            override fun getConfiguration(): Configuration = Flyway.configure()

            override fun getConnection(): Connection = connection
        }
}

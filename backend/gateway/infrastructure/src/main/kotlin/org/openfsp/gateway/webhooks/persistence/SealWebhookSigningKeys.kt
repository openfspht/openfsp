package org.openfsp.gateway.webhooks.persistence

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.springframework.stereotype.Component

@Component
class SealWebhookSigningKeys(
    private val seal: SigningKeySeal,
) : JavaMigration {
    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion(VERSION)

    override fun getDescription(): String = "seal webhook signing keys"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val connection = context.connection
        connection.createStatement().use {
            it.execute("alter table webhook_signing_keys add column sealing_key_id text, add column private_key_nonce bytea, add column private_key_ciphertext bytea")
        }
        val plaintext =
            connection.createStatement().use { statement ->
                statement.executeQuery("select kid, private_key from webhook_signing_keys").use { rows ->
                    generateSequence { if (rows.next()) rows.getString(1) to rows.getBytes(2) else null }.toList()
                }
            }
        connection.prepareStatement("update webhook_signing_keys set sealing_key_id = ?, private_key_nonce = ?, private_key_ciphertext = ? where kid = ?").use { update ->
            plaintext.forEach { (kid, privateKey) ->
                val sealed = seal.seal(kid, privateKey)
                update.setString(1, sealed.keyId)
                update.setBytes(2, sealed.nonce)
                update.setBytes(3, sealed.ciphertext)
                update.setString(4, kid)
                update.executeUpdate()
            }
        }
        connection.createStatement().use {
            it.execute(
                "alter table webhook_signing_keys alter column sealing_key_id set not null, alter column private_key_nonce set not null, " +
                    "alter column private_key_ciphertext set not null, drop column private_key",
            )
        }
    }

    private companion object {
        const val VERSION = "27"
    }
}

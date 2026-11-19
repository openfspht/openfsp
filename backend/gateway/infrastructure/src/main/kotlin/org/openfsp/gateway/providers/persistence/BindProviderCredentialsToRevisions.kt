package org.openfsp.gateway.providers.persistence

import org.flywaydb.core.api.MigrationVersion
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.api.migration.JavaMigration
import org.openfsp.gateway.providers.credentials.ProviderCredentialSeal
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.springframework.stereotype.Component
import javax.crypto.AEADBadTagException

@Component
class BindProviderCredentialsToRevisions(
    private val seal: ProviderCredentialSeal,
) : JavaMigration {
    private class SealedRow(
        val id: String,
        val account: String,
        val provider: String,
        val sealed: SealedCredentials,
    )

    override fun getVersion(): MigrationVersion = MigrationVersion.fromVersion(VERSION)

    override fun getDescription(): String = "bind provider credentials to their revision"

    override fun getChecksum(): Int? = null

    override fun canExecuteInTransaction(): Boolean = true

    override fun migrate(context: Context) {
        val connection = context.connection
        val rows =
            connection.prepareStatement("select id, account_id, provider, key_id, nonce, ciphertext from provider_credential_revisions where key_id = ? for update").use { query ->
                query.setString(1, seal.keyId)
                query.executeQuery().use { rows ->
                    generateSequence {
                        if (rows.next()) SealedRow(rows.getString(1), rows.getString(2), rows.getString(3), SealedCredentials(rows.getString(4), rows.getBytes(5), rows.getBytes(6))) else null
                    }.toList()
                }
            }
        connection.prepareStatement("update provider_credential_revisions set nonce = ?, ciphertext = ? where id = ?").use { update ->
            rows.forEach { row ->
                val plaintext = unboundPlaintext(row) ?: return@forEach
                val bound = seal.seal(row.account, row.provider, row.id, plaintext)
                update.setBytes(1, bound.nonce)
                update.setBytes(2, bound.ciphertext)
                update.setString(3, row.id)
                update.executeUpdate()
            }
        }
    }

    private fun unboundPlaintext(row: SealedRow): ByteArray? =
        try {
            seal.openUnboundToRevision(row.account, row.provider, row.sealed)
        } catch (_: AEADBadTagException) {
            seal.open(row.account, row.provider, row.id, row.sealed)
            null
        }

    private companion object {
        const val VERSION = "36"
    }
}

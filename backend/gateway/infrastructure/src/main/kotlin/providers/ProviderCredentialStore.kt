package org.openfsp.gateway.providers

import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.provider.CredentialMode
import org.openfsp.gateway.provider.CredentialRevision
import org.openfsp.gateway.provider.CredentialStatus
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import org.openfsp.gateway.providers.credentials.ProviderCredentialSeal
import org.openfsp.gateway.providers.credentials.SealedCredentials
import org.openfsp.protocol.ProblemType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.io.Serializable
import java.time.Instant
import javax.crypto.AEADBadTagException

@Entity
@Table(name = "provider_credential_revisions")
class ProviderCredentialRevisionJpaEntity(
    @Id val id: String,
    val accountId: String,
    val provider: String,
    val status: String,
    val names: String,
    val keyId: String?,
    val nonce: ByteArray?,
    val ciphertext: ByteArray?,
    val createdAt: Instant,
    val retiredAt: Instant?,
    val revokedAt: Instant?,
)

interface ProviderCredentialRevisionRepository : JpaRepository<ProviderCredentialRevisionJpaEntity, String> {
    fun findByAccountId(accountId: String): List<ProviderCredentialRevisionJpaEntity>

    @Query("SELECT r.id FROM ProviderCredentialRevisionJpaEntity r WHERE r.accountId = :account AND r.provider = :provider AND r.status = 'active'")
    fun activeId(
        account: String,
        provider: String,
    ): String?

    @Query(
        """
            SELECT r FROM ProviderCredentialRevisionJpaEntity r
            WHERE r.id = :id AND r.accountId = :account AND r.provider = :provider AND r.status IN ('active', 'retired') AND r.keyId IS NOT NULL
        """,
    )
    fun usable(
        id: String,
        account: String,
        provider: String,
    ): ProviderCredentialRevisionJpaEntity?

    @Modifying
    @Query("UPDATE ProviderCredentialRevisionJpaEntity r SET r.status = 'retired', r.retiredAt = :at WHERE r.accountId = :account AND r.provider = :provider AND r.status = 'active'")
    fun retireActive(
        account: String,
        provider: String,
        at: Instant,
    ): Int

    @Modifying
    @Query(
        """
            UPDATE ProviderCredentialRevisionJpaEntity r SET r.status = 'revoked', r.revokedAt = :at, r.keyId = NULL, r.nonce = NULL, r.ciphertext = NULL
            WHERE r.id = :id AND r.accountId = :account AND r.provider = :provider AND r.status <> 'revoked'
        """,
    )
    fun revoke(
        id: String,
        account: String,
        provider: String,
        at: Instant,
    ): Int

    @Modifying
    @Query(
        value = """
            UPDATE provider_credential_revisions SET key_id = NULL, nonce = NULL, ciphertext = NULL
            WHERE id IN (
                SELECT r.id FROM provider_credential_revisions r
                WHERE r.status = 'retired' AND r.key_id IS NOT NULL AND r.retired_at < :retiredBefore
                  AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.credential_revision = r.id AND p.status = 'pending')
                ORDER BY r.retired_at LIMIT :batch FOR UPDATE SKIP LOCKED
            )
        """,
        nativeQuery = true,
    )
    fun wipeRetired(
        retiredBefore: Instant,
        batch: Int,
    ): Int
}

@Embeddable
data class ProviderCredentialModeId(
    var accountId: String = "",
    var provider: String = "",
) : Serializable

@Entity
@Table(name = "provider_credential_modes")
class ProviderCredentialModeJpaEntity(
    @EmbeddedId var id: ProviderCredentialModeId,
    var mode: String,
    var changedAt: Instant,
)

interface ProviderCredentialModeRepository : JpaRepository<ProviderCredentialModeJpaEntity, ProviderCredentialModeId> {
    fun findByIdAccountId(accountId: String): List<ProviderCredentialModeJpaEntity>
}

@Component
class ProviderCredentialStore(
    private val repository: ProviderCredentialRevisionRepository,
    private val modes: ProviderCredentialModeRepository,
    private val json: JsonMapper,
    private val seal: ProviderCredentialSeal,
) : AccountCredentialStore {
    @Transactional
    override fun rotate(
        revision: CredentialRevision,
        credentials: Map<String, String>,
    ) {
        val sealed = seal.seal(revision.account, revision.provider, revision.id, json.writeValueAsBytes(credentials))
        repository.retireActive(revision.account, revision.provider, revision.createdAt)
        try {
            repository.saveAndFlush(
                ProviderCredentialRevisionJpaEntity(
                    revision.id,
                    revision.account,
                    revision.provider,
                    CredentialStatus.ACTIVE.wire,
                    credentials.keys.sorted().joinToString(SEPARATOR),
                    sealed.keyId,
                    sealed.nonce,
                    sealed.ciphertext,
                    revision.createdAt,
                    null,
                    null,
                ),
            )
        } catch (_: DataIntegrityViolationException) {
            throw OpenFspException(ProblemType.STATE_CONFLICT, "The credentials of this merchant account for ${revision.provider} changed concurrently: retry")
        }
    }

    @Transactional
    override fun retireActive(
        account: String,
        provider: String,
        at: Instant,
    ): Boolean = repository.retireActive(account, provider, at) > 0

    @Transactional
    override fun revoke(
        account: String,
        provider: String,
        revision: String,
        at: Instant,
    ): Boolean = repository.revoke(revision, account, provider, at) > 0

    override fun revisionsOf(account: String): List<CredentialRevision> = repository.findByAccountId(account).map(::toRevision)

    @Transactional
    override fun purgeRetired(
        retiredBefore: Instant,
        batch: Int,
    ): Int = repository.wipeRetired(retiredBefore, batch)

    override fun modesOf(account: String): Map<String, CredentialMode> =
        modes.findByIdAccountId(account).associate { it.id.provider to CredentialMode.fromWire(it.mode)!! }

    @Transactional
    override fun setMode(
        account: String,
        provider: String,
        mode: CredentialMode,
        at: Instant,
    ) {
        modes.save(ProviderCredentialModeJpaEntity(ProviderCredentialModeId(account, provider), mode.wire, at))
    }

    fun modeOf(
        account: String,
        provider: String,
    ): CredentialMode = modes.findById(ProviderCredentialModeId(account, provider)).map { CredentialMode.fromWire(it.mode)!! }.orElse(CredentialMode.PLATFORM)

    fun activeRevision(
        account: String,
        provider: String,
    ): String? = repository.activeId(account, provider)

    fun usableRevisions(
        account: String,
        provider: String,
    ): List<String> =
        repository
            .findByAccountId(account)
            .filter { it.provider == provider && it.keyId != null && it.status != CredentialStatus.REVOKED.wire }
            .sortedWith(compareBy<ProviderCredentialRevisionJpaEntity> { it.status != CredentialStatus.ACTIVE.wire }.thenByDescending { it.createdAt })
            .map { it.id }

    fun isUsable(
        account: String,
        provider: String,
        revision: String,
    ): Boolean = repository.usable(revision, account, provider) != null

    fun read(
        account: String,
        provider: String,
        revision: String,
    ): Map<String, String>? {
        val entity = repository.usable(revision, account, provider) ?: return null
        val sealed = SealedCredentials(entity.keyId!!, entity.nonce!!, entity.ciphertext!!)
        if (sealed.keyId != seal.keyId) {
            throw OpenFspException(
                ProblemType.PROVIDER_UNAVAILABLE,
                "The credentials of this merchant account for $provider were sealed under another application key: the operator sets them again from the console",
            )
        }
        val plaintext =
            try {
                seal.open(account, provider, revision, sealed)
            } catch (_: AEADBadTagException) {
                throw OpenFspException(
                    ProblemType.PROVIDER_UNAVAILABLE,
                    "The credentials of this merchant account for $provider do not open for their revision: the operator sets them again from the console",
                )
            }
        return json.readValue(plaintext)
    }

    private fun toRevision(entity: ProviderCredentialRevisionJpaEntity) =
        CredentialRevision(
            id = entity.id,
            account = entity.accountId,
            provider = entity.provider,
            names = entity.names.split(SEPARATOR).filter { it.isNotEmpty() },
            status = CredentialStatus.fromWire(entity.status),
            createdAt = entity.createdAt,
            retiredAt = entity.retiredAt,
            revokedAt = entity.revokedAt,
            sealed = entity.keyId != null,
        )

    private companion object {
        const val SEPARATOR = " "
    }
}

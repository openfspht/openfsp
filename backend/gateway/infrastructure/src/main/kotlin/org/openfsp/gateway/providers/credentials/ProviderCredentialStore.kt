package org.openfsp.gateway.providers.credentials

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.shared.security.ApplicationKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.io.Serializable
import java.time.Instant

data class ProviderCredentialId(
    val accountId: String = "",
    val provider: String = "",
) : Serializable

@Entity
@Table(name = "provider_credentials")
@IdClass(ProviderCredentialId::class)
class ProviderCredentialJpaEntity(
    @Id val accountId: String,
    @Id val provider: String,
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
    val names: String,
    val updatedAt: Instant,
)

interface ProviderCredentialJpaRepository : JpaRepository<ProviderCredentialJpaEntity, ProviderCredentialId> {
    fun findByAccountId(accountId: String): List<ProviderCredentialJpaEntity>

    @Query("select c.updatedAt from ProviderCredentialJpaEntity c where c.accountId = :account and c.provider = :provider")
    fun updatedAt(
        account: String,
        provider: String,
    ): Instant?

    @Modifying
    @Query("delete from ProviderCredentialJpaEntity c where c.accountId = :account and c.provider = :provider")
    fun remove(
        account: String,
        provider: String,
    ): Int
}

data class StoredCredentials(
    val provider: String,
    val names: List<String>,
    val updatedAt: Instant,
)

@Component
class ProviderCredentialStore(
    private val repository: ProviderCredentialJpaRepository,
    private val json: JsonMapper,
    private val calendar: BusinessCalendar,
    applicationKey: ApplicationKey,
) {
    private val cipher = CredentialCipher(applicationKey.derive(ApplicationKey.Purpose.PROVIDER_CREDENTIALS))

    @Transactional
    fun save(
        account: String,
        provider: String,
        credentials: Map<String, String>,
    ) {
        val sealed = cipher.seal(json.writeValueAsBytes(credentials), contextOf(account, provider))
        repository.save(ProviderCredentialJpaEntity(account, provider, sealed.keyId, sealed.nonce, sealed.ciphertext, credentials.keys.sorted().joinToString(SEPARATOR), calendar.now()))
    }

    @Transactional
    fun remove(
        account: String,
        provider: String,
    ): Boolean = repository.remove(account, provider) > 0

    fun of(account: String): List<StoredCredentials> = repository.findByAccountId(account).map { StoredCredentials(it.provider, it.names.split(SEPARATOR), it.updatedAt) }

    fun updatedAt(
        account: String,
        provider: String,
    ): Instant? = repository.updatedAt(account, provider)

    fun read(
        account: String,
        provider: String,
    ): Map<String, String>? {
        val entity = repository.findById(ProviderCredentialId(account, provider)).orElse(null) ?: return null
        val sealed = SealedCredentials(entity.keyId, entity.nonce, entity.ciphertext)
        if (sealed.keyId != cipher.keyId) {
            throw OpenFspException(
                ProblemType.PROVIDER_UNAVAILABLE,
                "The credentials of this merchant account for $provider were sealed under another application key: the operator sets them again from the console",
            )
        }
        return json.readValue(cipher.open(sealed, contextOf(account, provider)))
    }

    private fun contextOf(
        account: String,
        provider: String,
    ) = "$CONTEXT\u0000$account\u0000$provider"

    private companion object {
        const val CONTEXT = "openfsp-provider-credentials"
        const val SEPARATOR = " "
    }
}

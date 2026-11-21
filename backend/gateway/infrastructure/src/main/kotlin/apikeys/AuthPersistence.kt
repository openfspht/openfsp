package org.openfsp.gateway.apikeys

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.openfsp.gateway.auth.ApiKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.spi.ApiKeys
import org.openfsp.protocol.ResourceIds
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.JpaSpecificationExecutor
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.HexFormat

@Entity
@Table(name = "principals")
class PrincipalJpaEntity(
    @Id val id: String,
    val accountId: String,
    val name: String,
    val createdAt: Instant,
)

@Entity
@Table(name = "api_keys")
class ApiKeyJpaEntity(
    @Id val id: String,
    val principalId: String,
    val accountId: String,
    val lookupPrefix: String,
    val digest: ByteArray,
    val scopes: String,
    val environment: String,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val revokedAt: Instant? = null,
    val lastUsedAt: Instant? = null,
    val demo: Boolean = false,
)

interface PrincipalJpaRepository : JpaRepository<PrincipalJpaEntity, String> {
    fun findByAccountIdAndName(
        accountId: String,
        name: String,
    ): PrincipalJpaEntity?

    @Modifying
    @Query("INSERT INTO principals (id, account_id, name, created_at) VALUES (:id, :account, :name, :at) ON CONFLICT (account_id, name) DO NOTHING", nativeQuery = true)
    fun insertIfAbsent(
        id: String,
        account: String,
        name: String,
        at: Instant,
    ): Int
}

interface ApiKeyJpaRepository :
    JpaRepository<ApiKeyJpaEntity, String>,
    JpaSpecificationExecutor<ApiKeyJpaEntity> {
    fun findByLookupPrefix(lookupPrefix: String): List<ApiKeyJpaEntity>

    fun findByDigest(digest: ByteArray): ApiKeyJpaEntity?

    @Query("SELECT COUNT(k) FROM ApiKeyJpaEntity k WHERE k.revokedAt IS NULL AND (k.expiresAt IS NULL OR k.expiresAt > :now) AND (k.demo = FALSE OR :demo = TRUE)")
    fun countActive(
        now: Instant,
        demo: Boolean,
    ): Long

    @Modifying
    @Query("UPDATE ApiKeyJpaEntity k SET k.lastUsedAt = :at WHERE k.id = :id AND k.revokedAt IS NULL AND (k.lastUsedAt IS NULL OR k.lastUsedAt < :at)")
    fun touch(
        id: String,
        at: Instant,
    ): Int

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE ApiKeyJpaEntity k SET k.revokedAt = :at, k.digest = :digest WHERE k.id = :id AND k.revokedAt IS NULL")
    fun revoke(
        id: String,
        at: Instant,
        digest: ByteArray,
    ): Int
}

@Component
class ApiKeyEntityMapper {
    private val hex = HexFormat.of()

    fun toDomain(entity: ApiKeyJpaEntity) =
        ApiKey(
            id = entity.id,
            principalId = entity.principalId,
            accountId = entity.accountId,
            lookupPrefix = entity.lookupPrefix,
            digest = hex.formatHex(entity.digest),
            scopes = scopes(entity.scopes),
            environment = entity.environment,
            createdAt = entity.createdAt,
            expiresAt = entity.expiresAt,
            revokedAt = entity.revokedAt,
            demo = entity.demo,
        )

    fun toEntity(key: ApiKey) =
        ApiKeyJpaEntity(
            id = key.id,
            principalId = key.principalId,
            accountId = key.accountId,
            lookupPrefix = key.lookupPrefix,
            digest = hex.parseHex(key.digest),
            scopes = key.scopes.joinToString(" ") { it.wire },
            environment = key.environment,
            createdAt = key.createdAt,
            expiresAt = key.expiresAt,
            revokedAt = key.revokedAt,
            demo = key.demo,
        )

    fun digest(value: String): ByteArray = hex.parseHex(value)

    fun scopes(value: String): Set<Scope> = value.split(' ').mapNotNull(Scope::fromWire).toSet()
}

@Component
class JpaApiKeys(
    private val keys: ApiKeyJpaRepository,
    private val principals: PrincipalJpaRepository,
    private val mapper: ApiKeyEntityMapper,
) : ApiKeys {
    override fun withPrefix(lookupPrefix: String): List<ApiKey> = keys.findByLookupPrefix(lookupPrefix).map(mapper::toDomain)

    override fun withDigest(digest: String): ApiKey? = keys.findByDigest(mapper.digest(digest))?.let(mapper::toDomain)

    override fun principalNamed(
        account: String,
        name: String,
        at: Instant,
    ): String {
        principals.findByAccountIdAndName(account, name)?.let { return it.id }
        principals.insertIfAbsent(ResourceIds.generate("prn"), account, name, at)
        return principals.findByAccountIdAndName(account, name)!!.id
    }

    override fun save(key: ApiKey) {
        keys.save(mapper.toEntity(key))
    }

    @Transactional
    override fun touch(
        keyId: String,
        at: Instant,
    ) {
        keys.touch(keyId, at)
    }

    @Transactional
    override fun revoke(
        keyId: String,
        at: Instant,
    ): ApiKey? {
        if (keys.revoke(keyId, at, ByteArray(DIGEST_BYTES)) != 1) return null
        return keys.findById(keyId).map(mapper::toDomain).orElse(null)
    }

    override fun count(): Long = keys.count()

    private companion object {
        const val DIGEST_BYTES = 32
    }
}

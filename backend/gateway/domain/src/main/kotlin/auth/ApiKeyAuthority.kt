package org.openfsp.gateway.auth

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.spi.MerchantAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.auth.spi.ApiKeys
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.protocol.ResourceIds
import org.openfsp.server.Digests
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant

@DomainService
class ApiKeyAuthority(
    private val keys: ApiKeys,
    private val accounts: MerchantAccounts,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
    private val environment: GatewayEnvironment,
    private val transactions: Transactions,
) : AuthenticateKey,
    ManageKeys {
    private val random = SecureRandom()

    override fun authenticate(presented: String): AuthenticatedKey? {
        val parsed = PresentedKey.parse(presented)
        val digest = digest(presented)
        var found: ApiKey? = null
        for (candidate in parsed?.let { keys.withPrefix(it.lookupPrefix) }.orEmpty().ifEmpty { listOf(null) }) {
            val matches = MessageDigest.isEqual((candidate?.digest ?: DUMMY).toByteArray(), digest.toByteArray())
            if (matches && candidate != null) found = candidate
        }
        val now = calendar.now()
        val key = found?.takeIf { parsed?.environment == environment.name && it.usableAt(now, environment) } ?: return null
        keys.touch(key.id, now)
        return AuthenticatedKey(key.id, key.principalId, key.accountId, key.lookupPrefix, key.scopes)
    }

    override fun issue(
        account: String,
        principal: String,
        scopes: Set<Scope>,
        expiresAt: Instant?,
    ): IssuedKey {
        val secret = PresentedKey.prefixOf(environment.name) + (1..SECRET_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        val granted = scopes + Scope.ALWAYS_GRANTED
        val key = store(account, principal, secret, granted, expiresAt)
        trace(AuditKind.KEY_ISSUED, key.id, key.lookupPrefix, granted)
        return IssuedKey(key.id, secret, key.principalId, key.accountId)
    }

    override fun register(
        account: String,
        principal: String,
        secret: String,
        scopes: Set<Scope>,
    ): String? {
        val parsed = PresentedKey.parse(secret)
        require(parsed != null && parsed.environment == environment.name) { "the key does not follow ${PresentedKey.prefixOf(environment.name)}<secret>" }
        find(secret)?.let { return it }
        if (keys.withPrefix(parsed.lookupPrefix).any { it.revokedAt != null }) return null
        val granted = scopes + Scope.ALWAYS_GRANTED
        val stored = runCatching { store(account, principal, secret, granted, null) }.getOrElse { conflict -> return find(secret) ?: throw conflict }
        return stored.id.also { trace(AuditKind.KEY_ISSUED, it, parsed.lookupPrefix, granted) }
    }

    override fun find(secret: String): String? = keys.withDigest(digest(secret))?.id

    override fun revoke(keyId: String): Boolean {
        val revoked = keys.revoke(keyId, calendar.now()) ?: return false
        trace(AuditKind.KEY_REVOKED, keyId, revoked.lookupPrefix, revoked.scopes)
        return true
    }

    private fun trace(
        kind: AuditKind,
        keyId: String,
        prefix: String?,
        scopes: Set<Scope>,
    ) = audit.record(
        AuditRecord(calendar.now(), kind, null, prefix, kind.wire, null, keyId, scopes.joinToString(" ") { it.wire }.ifEmpty { null }),
    )

    override fun isEmpty(): Boolean = keys.count() == 0L

    private fun store(
        account: String,
        principal: String,
        secret: String,
        scopes: Set<Scope>,
        expiresAt: Instant?,
    ): ApiKey {
        val now = calendar.now()
        if (accounts.find(account)?.isActiveAt(now) != true) throw OpenFspException.invalid("account", "is not an active merchant account")
        return transactions.inTransaction {
            val key =
                ApiKey(
                    id = ResourceIds.generate("key"),
                    principalId = keys.principalNamed(account, principal, now),
                    accountId = account,
                    lookupPrefix = PresentedKey.parse(secret)!!.lookupPrefix,
                    digest = digest(secret),
                    scopes = scopes,
                    environment = environment.name,
                    createdAt = now,
                    expiresAt = expiresAt,
                    demo = environment.demo,
                )
            keys.save(key)
            key
        }
    }

    private fun digest(key: String): String = Digests.sha256Hex(key)

    private companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        const val SECRET_LENGTH = 40
        val DUMMY = "0".repeat(64)
    }
}

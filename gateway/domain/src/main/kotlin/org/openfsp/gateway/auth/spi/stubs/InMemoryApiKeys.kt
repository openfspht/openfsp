package org.openfsp.gateway.auth.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.auth.ApiKey
import org.openfsp.gateway.auth.KeySummary
import org.openfsp.gateway.auth.spi.ApiKeys
import java.time.Instant

@Stub
class InMemoryApiKeys : ApiKeys {
    private val keys = mutableMapOf<String, ApiKey>()
    private val principals = mutableMapOf<String, String>()
    private val lastUse = mutableMapOf<String, Instant>()

    override fun withPrefix(lookupPrefix: String): List<ApiKey> = keys.values.filter { it.lookupPrefix == lookupPrefix }

    override fun withDigest(digest: String): ApiKey? = keys.values.firstOrNull { it.digest == digest }

    override fun principalNamed(
        name: String,
        at: Instant,
    ): String = principals.getOrPut(name) { "prn_$name" }

    override fun save(key: ApiKey) {
        keys[key.id] = key
    }

    override fun touch(
        keyId: String,
        at: Instant,
    ) {
        lastUse[keyId] = at
    }

    override fun revoke(
        keyId: String,
        at: Instant,
    ): Boolean {
        val key = keys[keyId]?.takeIf { it.revokedAt == null } ?: return false
        keys[keyId] = key.copy(revokedAt = at, digest = "")
        return true
    }

    override fun summaries(): List<KeySummary> =
        keys.values.map { key ->
            KeySummary(key.id, principals.entries.first { it.value == key.principalId }.key, key.lookupPrefix, key.scopes, key.createdAt, key.expiresAt, lastUse[key.id], key.revokedAt)
        }

    override fun count(): Long = keys.size.toLong()
}

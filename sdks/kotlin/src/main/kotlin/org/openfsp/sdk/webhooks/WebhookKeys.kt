package org.openfsp.sdk.webhooks

import org.openfsp.protocol.webhook.Ed25519Keys
import org.openfsp.protocol.webhook.PublicKeys
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.sdk.model.WebhookKey
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class WebhookKeys(
    private val source: () -> List<WebhookKey>,
    private val clock: Clock,
) : PublicKeys {
    @Volatile
    private var keys: Map<String, PublicKey> = emptyMap()
    private var refreshedAt: Instant? = null

    override fun resolve(keyId: String): PublicKey? = keys[keyId] ?: refresh(keyId)

    @Synchronized
    private fun refresh(keyId: String): PublicKey? {
        keys[keyId]?.let { return it }
        val now = clock.instant()
        if (refreshedAt?.let { now.isBefore(it.plus(REFRESH_INTERVAL)) } == true) return null
        refreshedAt = now
        keys = source().mapNotNull { key -> key.publicKeyOrNull()?.let { key.keyId to it } }.toMap()
        return keys[keyId]
    }

    private fun WebhookKey.publicKeyOrNull(): PublicKey? {
        val raw = x ?: return null
        if (status == null || keyType != WebhookJwk.OKP || curve != WebhookJwk.ED25519_CURVE) return null
        if (algorithm != null && algorithm != WebhookJwk.EDDSA) return null
        if (use != null && use != WebhookJwk.SIG) return null
        return runCatching { Ed25519Keys.fromRaw(raw) }.getOrNull()
    }

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}

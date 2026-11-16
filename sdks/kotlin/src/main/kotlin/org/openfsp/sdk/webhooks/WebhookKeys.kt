package org.openfsp.sdk.webhooks

import org.openfsp.protocol.webhook.KeyStatus
import org.openfsp.protocol.webhook.PublicKeys
import org.openfsp.protocol.webhook.WebhookJwk
import java.security.PublicKey
import java.time.Clock
import java.time.Duration
import java.time.Instant

internal class WebhookKeys(
    private val source: () -> List<WebhookJwk>,
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
        keys = source().filter { KeyStatus.fromWire(it.openfspStatus) != null }.mapNotNull { jwk -> jwk.publicKeyOrNull()?.let { jwk.kid to it } }.toMap()
        return keys[keyId]
    }

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofSeconds(10)
    }
}

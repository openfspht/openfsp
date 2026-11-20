package org.openfsp.sdk.webhooks

import org.openfsp.protocol.webhook.PublicKeys
import org.openfsp.protocol.webhook.WebhookJwk
import org.openfsp.sdk.WebhookKeysUnavailableException
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
    private var failedAt: Instant? = null

    override fun resolve(keyId: String): PublicKey? = keys[keyId] ?: refresh(keyId)

    @Synchronized
    private fun refresh(keyId: String): PublicKey? {
        keys[keyId]?.let { return it }
        val now = clock.instant()
        if (failedAt?.let { now.isBefore(it.plus(FAILURE_BACKOFF)) } == true) throw unavailable(null)
        if (refreshedAt?.let { now.isBefore(it.plus(REFRESH_INTERVAL)) } == true) return null
        val loaded =
            try {
                source()
            } catch (failure: RuntimeException) {
                failedAt = now
                throw unavailable(failure)
            }
        keys = loaded.mapNotNull { key -> key.publicKeyOrNull()?.let { key.keyId to it } }.toMap()
        refreshedAt = now
        failedAt = null
        return keys[keyId]
    }

    private fun unavailable(cause: Throwable?) = WebhookKeysUnavailableException("the webhook keys could not be loaded", cause)

    private fun WebhookKey.publicKeyOrNull(): PublicKey? {
        if (status == null || (use != null && use != WebhookJwk.SIG)) return null
        return WebhookJwk.publicKeyOf(keyType, curve, algorithm, x, y)
    }

    private companion object {
        val REFRESH_INTERVAL: Duration = Duration.ofSeconds(10)
        val FAILURE_BACKOFF: Duration = Duration.ofSeconds(1)
    }
}

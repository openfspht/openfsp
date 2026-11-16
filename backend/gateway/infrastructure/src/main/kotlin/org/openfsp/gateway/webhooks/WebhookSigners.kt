package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhooks.persistence.JpaSigningKeys
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

@Component
class WebhookSigners(
    private val rotation: RotateSigningKeys,
    private val keys: JpaSigningKeys,
    private val properties: WebhookProperties,
    private val clock: Clock,
) {
    private val signers = ConcurrentHashMap<String, WebhookSigner>()

    @Volatile
    private var current: Pair<String, Instant>? = null

    @EventListener(ApplicationReadyEvent::class)
    fun provisionFromEnvironment() {
        val key = properties.signingKey ?: return
        keys.provision(key.id, Base64.getDecoder().decode(key.privateKey), Base64.getDecoder().decode(key.publicKey))
    }

    fun signer(): WebhookSigner {
        val now = clock.instant()
        val kid = current?.takeIf { Duration.between(it.second, now) < RECHECK }?.first ?: rotation.current().also { current = it to now }
        return signers.computeIfAbsent(kid) { WebhookSigner(keys.privateKey(it), it, clock) }
    }

    fun forget() {
        current = null
    }

    private companion object {
        val RECHECK: Duration = Duration.ofSeconds(30)
    }
}

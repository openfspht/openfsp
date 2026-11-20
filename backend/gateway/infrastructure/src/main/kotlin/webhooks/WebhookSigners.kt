package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.ProvidedSigningKey
import org.openfsp.gateway.webhook.api.RotateSigningKeys
import org.openfsp.gateway.webhook.spi.SigningKeyEvents
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class SigningKeyRotated(
    val kid: String,
)

@Component
class SpringSigningKeyEvents(
    private val publisher: ApplicationEventPublisher,
) : SigningKeyEvents {
    override fun rotated(kid: String) = publisher.publishEvent(SigningKeyRotated(kid))
}

@Component
class WebhookSigners(
    private val rotation: RotateSigningKeys,
    private val properties: WebhookProperties,
    private val clock: Clock,
) {
    private val signers = ConcurrentHashMap<String, WebhookSigner>()

    @Volatile
    private var current: Pair<String, Instant>? = null

    @EventListener(ApplicationReadyEvent::class)
    fun provisionAtStartup() {
        rotation.provisionAtStartup(properties.signingKey?.let { ProvidedSigningKey(it.id, Base64.getDecoder().decode(it.privateKey), Base64.getDecoder().decode(it.publicKey)) })
    }

    fun signer(): WebhookSigner {
        val now = clock.instant()
        val kid = current?.takeIf { Duration.between(it.second, now) < RECHECK }?.first ?: rotation.current().also { current = it to now }
        return signers.computeIfAbsent(kid) { WebhookSigner(rotation.privateKey(it), it, clock) }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    fun on(event: SigningKeyRotated) {
        current = null
    }

    private companion object {
        val RECHECK: Duration = Duration.ofSeconds(30)
    }
}

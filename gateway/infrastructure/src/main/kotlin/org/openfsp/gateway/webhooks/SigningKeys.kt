package org.openfsp.gateway.webhooks

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.persistence.webhook.SigningKeyJpaEntity
import org.openfsp.gateway.persistence.webhook.SigningKeyJpaRepository
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

data class PublishedKey(
    val kid: String,
    val publicKey: java.security.PublicKey,
    val status: String,
)

@Component
class SigningKeys(
    private val repository: SigningKeyJpaRepository,
    private val properties: GatewayProperties,
    private val calendar: BusinessCalendar,
    private val clock: Clock,
) {
    @Volatile
    private var signer: WebhookSigner? = null

    fun signer(): WebhookSigner =
        signer ?: synchronized(this) {
            signer ?: run {
                val active = repository.findByOrderByCreatedAtDesc().firstOrNull { it.status == ACTIVE } ?: generate()
                WebhookSigner(KeyFactory.getInstance(ED25519).generatePrivate(PKCS8EncodedKeySpec(active.privateKey)), active.kid, clock).also { signer = it }
            }
        }

    fun published(): List<PublishedKey> {
        signer()
        return repository.findByOrderByCreatedAtDesc().map {
            PublishedKey(it.kid, KeyFactory.getInstance(ED25519).generatePublic(X509EncodedKeySpec(it.publicKey)), it.status)
        }
    }

    private fun generate(): SigningKeyJpaEntity {
        check(properties.developmentMode) { "no active webhook signing key: provision one before serving in this environment" }
        val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
        val now = calendar.now()
        return repository.saveAndFlush(SigningKeyJpaEntity("gw-" + KID_FORMAT.format(now), pair.private.encoded, pair.public.encoded, ACTIVE, now))
    }

    private companion object {
        const val ED25519 = "Ed25519"
        const val ACTIVE = "active"
        val KID_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss").withZone(ZoneOffset.UTC)
    }
}

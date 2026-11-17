package org.openfsp.gateway.webhooks.persistence

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.webhook.SigningKey
import org.openfsp.gateway.webhook.spi.SigningKeyStore
import org.openfsp.protocol.ResourceIds
import org.springframework.stereotype.Component
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

@Component
class JpaSigningKeys(
    private val repository: SigningKeyJpaRepository,
    private val calendar: BusinessCalendar,
) : SigningKeyStore {
    override fun all(): List<SigningKey> = repository.findAll().map { SigningKey(it.kid, it.activatesAt, it.retiredAt) }

    override fun create(activatesAt: Instant): String {
        val pair = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
        return store(ResourceIds.generate(KID_PREFIX), pair.private.encoded, pair.public.encoded, activatesAt)
    }

    override fun retire(
        kid: String,
        at: Instant,
    ) {
        repository.findById(kid).filter { it.retiredAt == null }.ifPresent { repository.save(it.also { key -> key.retiredAt = at }) }
    }

    override fun withdraw(kid: String) {
        repository.findById(kid).ifPresent { repository.save(it.also { key -> key.retiredAt = Instant.EPOCH }) }
    }

    override fun lockRotation(): Boolean = repository.tryAdvisoryLock(ROTATION_LOCK)

    override fun lockKeys() {
        repository.advisoryLock(KEYS_LOCK)
    }

    override fun provision(
        kid: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
        activatesAt: Instant,
    ): Boolean {
        if (repository.existsById(kid)) return false
        store(kid, privateKey, publicKey, activatesAt)
        return true
    }

    override fun privateKey(kid: String): PrivateKey = KeyFactory.getInstance(ED25519).generatePrivate(PKCS8EncodedKeySpec(repository.findById(kid).orElseThrow().privateKey))

    override fun publicKey(kid: String): PublicKey = KeyFactory.getInstance(ED25519).generatePublic(X509EncodedKeySpec(repository.findById(kid).orElseThrow().publicKey))

    private fun store(
        kid: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
        activatesAt: Instant,
    ): String = repository.saveAndFlush(SigningKeyJpaEntity(kid, privateKey, publicKey, calendar.now(), activatesAt)).kid

    private companion object {
        const val ED25519 = "Ed25519"
        const val ROTATION_LOCK = 0x6f667370726f74L
        const val KEYS_LOCK = 0x6f6673706b6579L
        const val KID_PREFIX = "whk"
    }
}

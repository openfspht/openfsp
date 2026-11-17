package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.SigningKey
import org.openfsp.gateway.webhook.spi.SigningKeyStore
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.time.Instant

@Stub
class InMemorySigningKeys : SigningKeyStore {
    val keys = mutableListOf<SigningKey>()
    var locks = 0
    private val pairs = mutableMapOf<String, KeyPair>()

    override fun all(): List<SigningKey> = keys.toList()

    override fun create(activatesAt: Instant): String =
        "whk_${keys.size + 1}".also {
            keys += SigningKey(it, activatesAt, null)
            pairs[it] = KeyPairGenerator.getInstance(ED25519).generateKeyPair()
        }

    override fun provision(
        kid: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
        activatesAt: Instant,
    ): Boolean {
        if (keys.any { it.kid == kid }) return false
        val factory = KeyFactory.getInstance(ED25519)
        pairs[kid] = KeyPair(factory.generatePublic(X509EncodedKeySpec(publicKey)), factory.generatePrivate(PKCS8EncodedKeySpec(privateKey)))
        keys += SigningKey(kid, activatesAt, null)
        return true
    }

    override fun privateKey(kid: String): PrivateKey = pairs.getValue(kid).private

    override fun publicKey(kid: String): PublicKey = pairs.getValue(kid).public

    override fun retire(
        kid: String,
        at: Instant,
    ) = replace(kid) { it.copy(retiredAt = at) }

    override fun withdraw(kid: String) = replace(kid) { it.copy(retiredAt = Instant.EPOCH) }

    override fun lockRotation(): Boolean = true

    override fun lockKeys() {
        locks += 1
    }

    private fun replace(
        kid: String,
        change: (SigningKey) -> SigningKey,
    ) {
        keys.replaceAll { if (it.kid == kid) change(it) else it }
    }

    private companion object {
        const val ED25519 = "Ed25519"
    }
}

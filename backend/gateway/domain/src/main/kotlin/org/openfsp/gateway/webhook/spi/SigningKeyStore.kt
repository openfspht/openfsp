package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.SigningKey
import java.security.PrivateKey
import java.security.PublicKey
import java.time.Instant

interface SigningKeyStore {
    fun all(): List<SigningKey>

    fun create(activatesAt: Instant): String

    fun provision(
        kid: String,
        privateKey: ByteArray,
        publicKey: ByteArray,
        activatesAt: Instant,
    ): Boolean

    fun privateKey(kid: String): PrivateKey

    fun publicKey(kid: String): PublicKey

    fun retire(
        kid: String,
        at: Instant,
    )

    fun withdraw(kid: String)

    fun lockRotation(): Boolean

    fun lockKeys()
}

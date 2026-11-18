package org.openfsp.gateway.providers.credentials

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.HexFormat
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class SealedCredentials(
    val keyId: String,
    val nonce: ByteArray,
    val ciphertext: ByteArray,
)

class CredentialCipher(
    key: ByteArray,
) {
    private val secret = SecretKeySpec(key.copyOf(), ALGORITHM)
    private val random = SecureRandom()

    init {
        require(key.size == KEY_BYTES) { "the credentials key must hold exactly 256 bits" }
    }

    val keyId: String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key)).take(KEY_ID_LENGTH)

    fun seal(
        plaintext: ByteArray,
        context: String,
    ): SealedCredentials {
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, nonce)) }
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return SealedCredentials(keyId, nonce, cipher.doFinal(plaintext))
    }

    fun open(
        sealed: SealedCredentials,
        context: String,
    ): ByteArray {
        check(sealed.keyId == keyId) { "the credentials were sealed under another key" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.DECRYPT_MODE, secret, GCMParameterSpec(TAG_BITS, sealed.nonce)) }
        cipher.updateAAD(context.toByteArray(Charsets.UTF_8))
        return cipher.doFinal(sealed.ciphertext)
    }

    private companion object {
        const val ALGORITHM = "AES"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val NONCE_BYTES = 12
        const val TAG_BITS = 128
        const val KEY_ID_LENGTH = 16
    }
}

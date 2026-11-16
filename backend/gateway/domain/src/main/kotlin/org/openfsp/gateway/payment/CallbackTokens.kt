package org.openfsp.gateway.payment

import java.security.MessageDigest
import java.util.Base64
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class CallbackTokens(
    derivationKey: ByteArray,
) {
    // A labelled hash, not an HMAC, so no order id (HMAC of a client reference under the same key) can disclose this key.
    private val key = SecretKeySpec(MessageDigest.getInstance(DIGEST).digest(LABEL + derivationKey), MAC)

    fun forPayment(paymentId: String): String {
        val mac = Mac.getInstance(MAC).apply { init(key) }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(paymentId.toByteArray(Charsets.UTF_8)).copyOf(TOKEN_BYTES))
    }

    fun digest(token: String): String = HexFormat.of().formatHex(MessageDigest.getInstance(DIGEST).digest(token.toByteArray(Charsets.US_ASCII)))

    fun matches(
        token: String,
        digest: String?,
    ): Boolean = digest != null && MessageDigest.isEqual(digest(token).toByteArray(), digest.toByteArray())

    private companion object {
        const val DIGEST = "SHA-256"
        const val MAC = "HmacSHA256"
        const val TOKEN_BYTES = 16
        val LABEL = "openfsp callback token".toByteArray(Charsets.US_ASCII)
    }
}

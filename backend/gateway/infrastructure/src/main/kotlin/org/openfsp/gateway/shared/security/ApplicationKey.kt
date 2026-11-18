package org.openfsp.gateway.shared.security

import java.io.ByteArrayOutputStream
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class ApplicationKey(
    encoded: String,
) {
    private val master: ByteArray =
        runCatching { Base64.getDecoder().decode(encoded.trim()) }
            .getOrNull()
            ?.takeIf { it.size >= MIN_BYTES }
            ?: error("openfsp.app-key holds at least 256 random bits in base64: generate one with openssl rand -base64 32")

    fun derive(purpose: Purpose): ByteArray = hkdf(ByteArray(HASH_BYTES), master, "$LABEL/${purpose.label}".toByteArray(Charsets.UTF_8), DERIVED_BYTES)

    enum class Purpose(
        val label: String,
    ) {
        ORDER_IDS("order-ids"),
        CALLBACK_TOKENS("callback-tokens"),
        ADMIN_SESSIONS("admin-sessions"),
        PROVIDER_CREDENTIALS("provider-credentials"),
        WEBHOOK_SIGNING_KEYS("webhook-signing-keys"),
    }

    companion object {
        private const val LABEL = "openfsp"
        private const val ALGORITHM = "HmacSHA256"
        private const val HASH_BYTES = 32
        private const val MIN_BYTES = 32
        private const val DERIVED_BYTES = 32

        internal fun hkdf(
            salt: ByteArray,
            ikm: ByteArray,
            info: ByteArray,
            length: Int,
        ): ByteArray {
            val prk = hmac(salt, ikm)
            val output = ByteArrayOutputStream()
            var previous = ByteArray(0)
            var counter = 1
            while (output.size() < length) {
                previous = hmac(prk, previous + info + counter.toByte())
                output.write(previous)
                counter++
            }
            return output.toByteArray().copyOf(length)
        }

        private fun hmac(
            key: ByteArray,
            data: ByteArray,
        ): ByteArray = Mac.getInstance(ALGORITHM).apply { init(SecretKeySpec(key, ALGORITHM)) }.doFinal(data)
    }
}

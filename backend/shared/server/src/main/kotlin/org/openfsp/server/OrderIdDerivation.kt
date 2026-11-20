package org.openfsp.server

import org.openfsp.protocol.Base32
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class OrderIdDerivation(
    derivationKey: ByteArray,
) {
    private val key = SecretKeySpec(derivationKey.copyOf(), ALGORITHM)

    init {
        require(derivationKey.size >= MIN_KEY_BYTES) { "the derivation key must hold at least 256 bits" }
    }

    fun derive(
        account: String,
        reference: String,
    ): String {
        val mac = Mac.getInstance(ALGORITHM).apply { init(key) }
        mac.update(account.toByteArray(Charsets.UTF_8))
        mac.update(SEPARATOR)
        val digest = mac.doFinal(reference.toByteArray(Charsets.UTF_8))
        return Base32.rfc4648Lower(digest.copyOf(ORDER_ID_BYTES))
    }

    private companion object {
        const val ALGORITHM = "HmacSHA256"
        const val MIN_KEY_BYTES = 32
        const val ORDER_ID_BYTES = 16
        const val SEPARATOR: Byte = 0
    }
}

package org.openfsp.server

import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class Digests {
    fun hmacSha256Hex(
        key: String,
        value: String,
    ): String {
        val mac = Mac.getInstance(HMAC_SHA256).apply { init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256)) }
        return HEX.formatHex(mac.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    fun sha256Hex(value: String): String = HEX.formatHex(MessageDigest.getInstance(SHA256).digest(value.toByteArray(Charsets.UTF_8)))

    fun hexEquals(
        expected: String,
        presented: String?,
    ): Boolean = presented != null && secretEquals(expected.lowercase(), presented.lowercase())

    fun secretEquals(
        expected: String,
        presented: String?,
    ): Boolean = presented != null && MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), presented.toByteArray(Charsets.UTF_8))

    private companion object {
        const val HMAC_SHA256 = "HmacSHA256"
        const val SHA256 = "SHA-256"
        val HEX: HexFormat = HexFormat.of()
    }
}

package org.openfsp.simulator.dialects

import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_SHA256 = "HmacSHA256"
private val HEX: HexFormat = HexFormat.of()

fun hmacSha256Hex(
    key: String,
    value: ByteArray,
): String {
    val mac = Mac.getInstance(HMAC_SHA256).apply { init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC_SHA256)) }
    return HEX.formatHex(mac.doFinal(value))
}

fun hmacSha256Hex(
    key: String,
    value: String,
): String = hmacSha256Hex(key, value.toByteArray(Charsets.UTF_8))

fun sha256Hex(value: String): String = HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

fun constantTimeEquals(
    expected: String,
    presented: String?,
): Boolean = presented != null && MessageDigest.isEqual(expected.toByteArray(), presented.toByteArray())

package org.openfsp.gateway.providers.natcash

import java.security.MessageDigest
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class NatCashSignatures(
    private val privateKey: String,
    private val functionCode: String,
) {
    fun forPayment(
        partnerCode: String,
        username: String,
        password: String,
        timestamp: Long,
        requestId: String,
        orderNumber: String,
        amount: String,
    ): String = hmac(privateKey, sha256(privateKey + requestId) + partnerCode + username + password + timestamp + requestId + orderNumber + amount)

    fun forQuery(
        partnerCode: String,
        username: String,
        password: String,
        orderNumber: String,
        requestId: String,
    ): String = hmac(privateKey, sha256(privateKey + requestId) + partnerCode + username + password + orderNumber + requestId)

    fun forCallback(
        orderNumber: String,
        code: String,
    ): String = hmac(functionCode, sha256(functionCode + orderNumber) + orderNumber + code)

    fun matches(
        expected: String,
        presented: String,
    ): Boolean = MessageDigest.isEqual(expected.lowercase().toByteArray(), presented.lowercase().toByteArray())

    private fun sha256(value: String): String = HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)))

    private fun hmac(
        key: String,
        value: String,
    ): String {
        val mac = Mac.getInstance(HMAC)
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), HMAC))
        return HEX.formatHex(mac.doFinal(value.toByteArray(Charsets.UTF_8)))
    }

    private companion object {
        const val HMAC = "HmacSHA256"
        val HEX: HexFormat = HexFormat.of()
    }
}

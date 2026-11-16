package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.providers.hexEquals
import org.openfsp.gateway.providers.hmacSha256Hex
import org.openfsp.gateway.providers.sha256Hex

class NatCashSignatures(
    private val settings: NatCashSettings,
) {
    fun of(request: NatCashPaymentRequest): String =
        withPrivateKey(
            request.requestId,
            settings.partnerCode + settings.username + settings.password + request.timestamp + request.requestId + request.orderNumber +
                request.amount.toPlainString(),
        )

    fun of(query: NatCashTransactionQuery): String =
        withPrivateKey(query.requestId, settings.partnerCode + settings.username + settings.password + query.orderNumber + query.requestId)

    fun isAuthentic(callback: NatCashCallback): Boolean {
        val orderNumber = callback.orderNumber ?: return false
        val code = callback.code ?: return false
        val presented = callback.signature ?: return false
        val expected = hmacSha256Hex(settings.functionCode, sha256Hex(settings.functionCode + orderNumber) + orderNumber + code)
        return hexEquals(expected, presented)
    }

    private fun withPrivateKey(
        requestId: String,
        fields: String,
    ): String = hmacSha256Hex(settings.privateKey, sha256Hex(settings.privateKey + requestId) + fields)
}

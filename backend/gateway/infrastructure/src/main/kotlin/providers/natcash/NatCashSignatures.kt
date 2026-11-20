package org.openfsp.gateway.providers.natcash

import org.openfsp.server.Digests

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
        val expected = Digests.hmacSha256Hex(settings.functionCode, Digests.sha256Hex(settings.functionCode + orderNumber) + orderNumber + code)
        return Digests.hexEquals(expected, callback.signature)
    }

    private fun withPrivateKey(
        requestId: String,
        fields: String,
    ): String = Digests.hmacSha256Hex(settings.privateKey, Digests.sha256Hex(settings.privateKey + requestId) + fields)
}

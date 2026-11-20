package org.openfsp.simulator.dialects.natcash

import org.openfsp.server.Digests
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component

@Component
class NatCashSignatures(
    private val digests: Digests,
) {
    fun isAuthentic(
        settings: SimulatorProperties.Operator,
        request: NatCashPaymentRequest,
    ): Boolean =
        credentialsMatch(settings, request.partnerCode, request.username, request.password) &&
            digests.hexEquals(
                withPrivateKey(
                    settings,
                    request.requestId,
                    request.partnerCode + request.username + request.password + request.timestamp + request.requestId + request.orderNumber +
                        request.amount.toPlainString(),
                ),
                request.signature,
            )

    fun isAuthentic(
        settings: SimulatorProperties.Operator,
        query: NatCashTransactionQuery,
    ): Boolean =
        credentialsMatch(settings, query.partnerCode, query.username, query.password) &&
            digests.hexEquals(
                withPrivateKey(settings, query.requestId, query.partnerCode + query.username + query.password + query.orderNumber + query.requestId),
                query.signature,
            )

    fun forCallback(
        settings: SimulatorProperties.Operator,
        orderNumber: String,
        code: String,
    ): String {
        val functionCode = settings.credential(FUNCTION_CODE)
        return digests.hmacSha256Hex(functionCode, digests.sha256Hex(functionCode + orderNumber) + orderNumber + code)
    }

    private fun credentialsMatch(
        settings: SimulatorProperties.Operator,
        partnerCode: String,
        username: String,
        password: String,
    ): Boolean =
        digests.secretEquals(settings.credential(PARTNER_CODE), partnerCode) &&
            digests.secretEquals(settings.credential(USERNAME), username) &&
            digests.secretEquals(settings.credential(PASSWORD), password)

    private fun withPrivateKey(
        settings: SimulatorProperties.Operator,
        requestId: String,
        fields: String,
    ): String {
        val privateKey = settings.credential(PRIVATE_KEY)
        return digests.hmacSha256Hex(privateKey, digests.sha256Hex(privateKey + requestId) + fields)
    }

    companion object {
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PARTNER_CODE = "partner-code"
        const val PRIVATE_KEY = "private-key"
        const val FUNCTION_CODE = "function-code"
    }
}

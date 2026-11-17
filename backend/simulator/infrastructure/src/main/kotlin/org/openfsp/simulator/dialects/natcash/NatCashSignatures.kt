package org.openfsp.simulator.dialects.natcash

import org.openfsp.simulator.dialects.constantTimeEquals
import org.openfsp.simulator.dialects.hmacSha256Hex
import org.openfsp.simulator.dialects.sha256Hex
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.stereotype.Component

@Component
class NatCashSignatures {
    fun isAuthentic(
        settings: SimulatorProperties.Operator,
        request: NatCashPaymentRequest,
    ): Boolean =
        credentialsMatch(settings, request.partnerCode, request.username, request.password) &&
            constantTimeEquals(
                withPrivateKey(
                    settings,
                    request.requestId,
                    request.partnerCode + request.username + request.password + request.timestamp + request.requestId + request.orderNumber +
                        request.amount.toPlainString(),
                ),
                request.signature.lowercase(),
            )

    fun isAuthentic(
        settings: SimulatorProperties.Operator,
        query: NatCashTransactionQuery,
    ): Boolean =
        credentialsMatch(settings, query.partnerCode, query.username, query.password) &&
            constantTimeEquals(
                withPrivateKey(settings, query.requestId, query.partnerCode + query.username + query.password + query.orderNumber + query.requestId),
                query.signature.lowercase(),
            )

    fun forCallback(
        settings: SimulatorProperties.Operator,
        orderNumber: String,
        code: String,
    ): String {
        val functionCode = settings.credential(FUNCTION_CODE)
        return hmacSha256Hex(functionCode, sha256Hex(functionCode + orderNumber) + orderNumber + code)
    }

    private fun credentialsMatch(
        settings: SimulatorProperties.Operator,
        partnerCode: String,
        username: String,
        password: String,
    ): Boolean =
        constantTimeEquals(settings.credential(PARTNER_CODE), partnerCode) &&
            constantTimeEquals(settings.credential(USERNAME), username) &&
            constantTimeEquals(settings.credential(PASSWORD), password)

    private fun withPrivateKey(
        settings: SimulatorProperties.Operator,
        requestId: String,
        fields: String,
    ): String {
        val privateKey = settings.credential(PRIVATE_KEY)
        return hmacSha256Hex(privateKey, sha256Hex(privateKey + requestId) + fields)
    }

    companion object {
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PARTNER_CODE = "partner-code"
        const val PRIVATE_KEY = "private-key"
        const val FUNCTION_CODE = "function-code"
    }
}

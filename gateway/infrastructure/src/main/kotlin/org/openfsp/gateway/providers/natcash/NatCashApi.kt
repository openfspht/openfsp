package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction

class NatCashApi(
    private val payments: ProviderHttp,
    private val queries: ProviderHttp,
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
) {
    fun requestPayment(request: NatCashPaymentRequest): NatCashPaymentResponse {
        val answer = payments.post(CREDENTIAL, request)
        val response = read(answer, NatCashPaymentResponse::class.java)
        if (response.status != SUCCESS) throw rejected(answer, response.code, response.message)
        return response
    }

    fun checkTransaction(query: NatCashTransactionQuery): NatCashTransaction? {
        val answer = queries.post(CHECK_TRANSACTION, query)
        val response = read(answer, NatCashTransactionResponse::class.java)
        if (response.status == SUCCESS) return response.data
        if (response.code in UNSETTLED) return null
        throw rejected(answer, response.code, response.message)
    }

    private fun <T : Any> read(
        answer: ProviderAnswer,
        type: Class<T>,
    ): T {
        if (answer.isServerError) throw ProviderFailure.Rejected("NatCash answered ${answer.status}")
        return json.read(answer.body, type, PROVIDER)
    }

    private fun rejected(
        answer: ProviderAnswer,
        code: String?,
        message: String?,
    ) = ProviderFailure.Rejected("NatCash answered ${answer.status}", redaction.redact(mapOf("code" to code, "message" to message)))

    private companion object {
        const val PROVIDER = "NatCash"
        const val SUCCESS = 0
        const val CREDENTIAL = "/credential"
        const val CHECK_TRANSACTION = "/merchant/checkTransaction"
        val UNSETTLED = setOf("ERR_TRANSACTION_NOT_FOUND", "ERR_TRANSACTION_EXPIRED")
    }
}

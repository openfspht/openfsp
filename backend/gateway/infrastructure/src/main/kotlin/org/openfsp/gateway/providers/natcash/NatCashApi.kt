package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.common.BusinessCalendar
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
    private val calendar: BusinessCalendar,
) {
    fun requestPayment(request: NatCashPaymentRequest): NatCashPaymentResponse {
        val response = read(payments.post(CREDENTIAL, request), NatCashPaymentResponse::class.java) { it.status != null }
        if (response.status != SUCCESS) throw refused(response.code, response.message)
        return response
    }

    fun checkTransaction(query: NatCashTransactionQuery): NatCashTransaction? {
        val response = read(queries.post(CHECK_TRANSACTION, query), NatCashTransactionResponse::class.java) { it.status != null && (it.status != SUCCESS || it.data != null) }
        if (response.status == SUCCESS) return response.data
        if (response.code in UNSETTLED) return null
        throw refused(response.code, response.message)
    }

    private fun <T : Any> read(
        answer: ProviderAnswer,
        type: Class<T>,
        wellFormed: (T) -> Boolean,
    ): T {
        answer.rateLimited(PROVIDER, calendar.now())?.let { throw it }
        if (!answer.isSuccess) throw httpFailure(answer)
        return json.readOrNull(answer.body, type)?.takeIf(wellFormed) ?: throw ProviderFailure.Rejected("Unreadable response from $PROVIDER", mapOf("code" to INVALID_FORMAT))
    }

    private fun httpFailure(answer: ProviderAnswer): ProviderFailure.Rejected {
        val error = json.readOrNull(answer.body, NatCashError::class.java)
        return ProviderFailure.Rejected("$PROVIDER answered ${answer.status}", redaction.redact(mapOf("code" to (error?.code ?: answer.status.toString()), "message" to error?.message)))
    }

    private fun refused(
        code: String?,
        message: String?,
    ) = ProviderFailure.Rejected("$PROVIDER refused the request", redaction.redact(mapOf("code" to code, "message" to message)))

    private companion object {
        const val PROVIDER = "NatCash"
        const val SUCCESS = 0
        const val INVALID_FORMAT = "invalid_format"
        const val CREDENTIAL = "/credential"
        const val CHECK_TRANSACTION = "/merchant/checkTransaction"
        val UNSETTLED = setOf("ERR_TRANSACTION_NOT_FOUND", "ERR_TRANSACTION_EXPIRED")
    }
}

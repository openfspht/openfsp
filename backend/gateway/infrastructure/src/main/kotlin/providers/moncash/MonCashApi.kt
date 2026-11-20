package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.moncash.MonCashProvider.Companion.PROVIDER
import org.springframework.http.HttpHeaders

class MonCashApi(
    private val http: ProviderHttp,
    private val json: ProviderJson,
    private val token: MonCashAccessToken,
    private val errors: MonCashErrors,
) {
    fun createPayment(request: MonCashCreatePayment): MonCashCreatedPayment {
        val answer = authorized(CREATE_PAYMENT, request)
        if (!answer.isSuccess) throw errors.failure(answer)
        return json.read(answer.body, MonCashCreatedPayment::class.java, PROVIDER)
    }

    fun retrieveOrder(query: MonCashOrderQuery): MonCashPayment? = retrieve(RETRIEVE_ORDER, query)

    fun retrieveTransaction(query: MonCashTransactionQuery): MonCashPayment? = retrieve(RETRIEVE_TRANSACTION, query)

    private fun retrieve(
        path: String,
        query: Any,
    ): MonCashPayment? {
        val answer = authorized(path, query)
        if (answer.isSuccess) return json.read(answer.body, MonCashPaymentResult::class.java, PROVIDER).payment
        if (errors.isNoPayment(answer)) return null
        throw errors.failure(answer)
    }

    private fun authorized(
        path: String,
        body: Any,
    ): ProviderAnswer {
        val answer = http.post(path, body, mapOf(HttpHeaders.AUTHORIZATION to token.bearer(::requestToken)))
        if (answer.status != UNAUTHORIZED) return answer
        token.forget()
        return http.post(path, body, mapOf(HttpHeaders.AUTHORIZATION to token.bearer(::requestToken)))
    }

    private fun requestToken(): MonCashTokenResponse {
        val answer = http.post(TOKEN, headers = mapOf(HttpHeaders.AUTHORIZATION to token.basic), query = GRANT)
        if (!answer.isSuccess) throw errors.failure(answer)
        return json.read(answer.body, MonCashTokenResponse::class.java, PROVIDER)
    }

    private companion object {
        const val UNAUTHORIZED = 401
        const val TOKEN = "/oauth/token"
        const val CREATE_PAYMENT = "/v1/CreatePayment"
        const val RETRIEVE_ORDER = "/v1/RetrieveOrderPayment"
        const val RETRIEVE_TRANSACTION = "/v1/RetrieveTransactionPayment"
        val GRANT = mapOf("grant_type" to "client_credentials", "scope" to "read,write")
    }
}

package org.openfsp.gateway.providers.simulated

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction
import java.time.LocalDate

class SimulatedApi(
    private val http: ProviderHttp,
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
) {
    fun createPayment(request: SimulatedPaymentRequest): SimulatedOperatorPayment = read(http.post(PAYMENTS, request), SimulatedOperatorPayment::class.java)

    fun paymentOf(orderId: String): SimulatedOperatorPayment = read(http.get(BY_ORDER, orderId), SimulatedOperatorPayment::class.java)

    fun statement(day: LocalDate): SimulatedStatement = read(http.get(STATEMENTS, day), SimulatedStatement::class.java)

    private fun <T : Any> read(
        answer: ProviderAnswer,
        type: Class<T>,
    ): T {
        if (answer.isSuccess) return json.read(answer.body, type, PROVIDER)
        val error = json.readOrNull(answer.body, SimulatedError::class.java)
        val detail = error?.let { redaction.redact(mapOf("code" to it.code, "message" to it.message)) }
        throw ProviderFailure.Rejected("The provider answered ${answer.status}", detail)
    }

    private companion object {
        const val PROVIDER = "the provider"
        const val PAYMENTS = "/payments"
        const val BY_ORDER = "/payments/by-order/{orderId}"
        const val STATEMENTS = "/statements/{day}"
    }
}

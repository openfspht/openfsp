package org.openfsp.gateway.providers.openfsp

import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.ConfigurationResource
import org.openfsp.protocol.api.PaymentCollection
import org.openfsp.protocol.api.PaymentRequest
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.StatementCollection
import org.springframework.http.HttpHeaders
import java.time.Duration
import java.time.LocalDate

data class Announced<T>(
    val value: T,
    val maxAge: Duration?,
)

data class OperatorProblem(
    val type: String? = null,
    val title: String? = null,
    val detail: String? = null,
    val effect: String? = null,
)

class OpenFspApi(
    private val api: ProviderHttp,
    private val discovery: ProviderHttp,
    private val json: ProviderJson,
    private val redaction: SecretRedaction,
) {
    fun create(
        request: PaymentRequest,
        idempotencyKey: String,
    ): PaymentResource = read(api.post(PAYMENTS, request, mapOf(IDEMPOTENCY_KEY to idempotencyKey)), PaymentResource::class.java)

    fun byReference(reference: String): PaymentResource? = read(api.get(BY_REFERENCE, reference), PaymentCollection::class.java).data.firstOrNull()

    fun statement(
        provider: String,
        day: LocalDate,
    ) = read(api.get(STATEMENTS, provider, day), StatementCollection::class.java).data.firstOrNull()

    fun capabilities(): Announced<CapabilitiesResource> {
        val answer = api.get(CAPABILITIES)
        return Announced(read(answer, CapabilitiesResource::class.java), maxAge(answer))
    }

    fun configuration(): ConfigurationResource = read(discovery.get(CONFIGURATION), ConfigurationResource::class.java)

    private fun <T : Any> read(
        answer: ProviderAnswer,
        type: Class<T>,
    ): T {
        if (answer.isSuccess) return json.read(answer.body, type, PROVIDER)
        val problem = json.readOrNull(answer.body, OperatorProblem::class.java)
        val detail = problem?.let { mapOf("code" to it.type?.substringAfterLast('/'), "message" to (it.detail ?: it.title)).filterValues { value -> value != null } }?.takeIf { it.isNotEmpty() }
        val withoutEffect = answer.status in CLIENT_ERROR && problem?.effect != UNKNOWN_EFFECT
        throw ProviderFailure.Rejected("The provider answered ${answer.status}", detail?.let(redaction::redact), withoutEffect)
    }

    private fun maxAge(answer: ProviderAnswer): Duration? =
        answer.headers
            .getValuesAsList(HttpHeaders.CACHE_CONTROL)
            .firstNotNullOfOrNull { MAX_AGE.find(it) }
            ?.groupValues
            ?.get(1)
            ?.toLongOrNull()
            ?.let(Duration::ofSeconds)

    private companion object {
        val MAX_AGE = Regex("""(?:^|[,\s])max-age=(\d+)\s*(?:,|$)""", RegexOption.IGNORE_CASE)
        val CLIENT_ERROR = 400..499
        const val UNKNOWN_EFFECT = "unknown"
        const val PROVIDER = "the provider"
        const val IDEMPOTENCY_KEY = "Idempotency-Key"
        const val PAYMENTS = "/v1/payments"
        const val BY_REFERENCE = "/v1/payments?reference={reference}"
        const val STATEMENTS = "/v1/statements?provider={provider}&business_date={day}"
        const val CAPABILITIES = "/v1/capabilities"
        const val CONFIGURATION = "/.well-known/openfsp-configuration"
    }
}

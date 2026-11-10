package org.openfsp.gateway.providers.moncash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.spi.ORDER_ID
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderAnswer
import org.openfsp.gateway.providers.ProviderHttp
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.SecretRedaction
import org.openfsp.gateway.providers.text
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.util.UriComponentsBuilder
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64

class MonCashClient(
    override val id: String,
    override val limits: AmountLimits,
    settings: ProviderProperties.Provider,
    timeout: Duration,
    private val json: JsonMapper,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderOperations {
    override val descriptor = DESCRIPTOR

    private val http = ProviderHttp(settings.baseUrl, timeout)
    private val checkoutBase = settings.secondaryUrl ?: error("$id needs secondary-url, the MonCash middleware base")
    private val clientId = settings.credential(CLIENT_ID)
    private val clientSecret = settings.credential(CLIENT_SECRET)
    private val redaction = SecretRedaction(listOf(clientId, clientSecret))

    @Volatile
    private var token: Pair<String, Instant>? = null

    override fun create(request: ProviderPaymentRequest): ProviderPayment {
        val answer = authorized { bearer -> post("/v1/CreatePayment").header(HttpHeaders.AUTHORIZATION, bearer).body(mapOf("amount" to amounts.toNumber(request.amount), "orderId" to request.orderId)) }
        val body = readSuccess(answer)
        val paymentToken = body.path("payment_token")
        val value = paymentToken.path("token").text() ?: throw ProviderFailure.Rejected("MonCash returned no payment token")
        val redirect = UriComponentsBuilder.fromUri(checkoutBase).path("/Payment/Redirect").queryParam("token", value).build().toUriString()
        val expiresAt = paymentToken.path("expired").text()?.let(::expiry)
        return ProviderPayment(ProviderReport(request.orderId, null, PaymentStatus.PENDING, null), NextAction.redirect(redirect, expiresAt), expiresAt)
    }

    override fun lookup(orderId: String): ProviderReport {
        val answer = authorized { bearer -> post("/v1/RetrieveOrderPayment").header(HttpHeaders.AUTHORIZATION, bearer).body(mapOf("orderId" to orderId)) }
        if (!answer.isSuccess) {
            if (answer.status in SERVER_ERRORS) throw rejected(answer)
            return ProviderReport(orderId, null, PaymentStatus.PENDING, null)
        }
        val payment = json.readTree(answer.body).path("payment")
        val message = payment.path("message").text()
        val status =
            when (message) {
                SUCCESSFUL -> PaymentStatus.SUCCEEDED
                FAILED -> PaymentStatus.FAILED
                else -> PaymentStatus.PENDING
            }
        return ProviderReport(
            orderId = payment.path("reference").text() ?: orderId,
            providerReference = payment.path("transaction_id").text(),
            status = status,
            amount = payment.path("cost").text()?.let(amounts::fromDecimal),
            failureReason = FailureReason.UNSPECIFIED.takeIf { status == PaymentStatus.FAILED },
            failureDetail = mapOf("code" to message, "message" to message).takeIf { status == PaymentStatus.FAILED },
        )
    }

    override fun returnedOrder(parameters: Map<String, String>): String? {
        parameters[ORDER_ID_PARAMETER]?.let { return it }
        parameters[ORDER_ID]?.let { return it }
        val transactionId = parameters[TRANSACTION_ID_PARAMETER] ?: return null
        val answer = authorized { bearer -> post("/v1/RetrieveTransactionPayment").header(HttpHeaders.AUTHORIZATION, bearer).body(mapOf("transactionId" to transactionId)) }
        if (!answer.isSuccess) return null
        return json.readTree(answer.body).path("payment").path("reference").text()
    }

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? = null

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    override fun statement(day: LocalDate): ProviderStatement = throw UnsupportedOperationException("MonCash offers no statement")

    private fun authorized(call: org.springframework.web.client.RestClient.(String) -> org.springframework.web.client.RestClient.RequestHeadersSpec<*>): ProviderAnswer {
        val answer = http.exchange { call("Bearer ${accessToken()}") }
        if (answer.status != UNAUTHORIZED) return answer
        token = null
        return http.exchange { call("Bearer ${accessToken()}") }
    }

    private fun accessToken(): String {
        token?.takeIf { calendar.now().isBefore(it.second) }?.let { return it.first }
        val basic = Base64.getEncoder().encodeToString("$clientId:$clientSecret".toByteArray())
        val answer =
            http.exchange {
                post()
                    .uri { it.path("/oauth/token").queryParam("grant_type", "client_credentials").queryParam("scope", "read,write").build() }
                    .header(HttpHeaders.AUTHORIZATION, "Basic $basic")
                    .accept(MediaType.APPLICATION_JSON)
            }
        val body = readSuccess(answer)
        val value = body.path("access_token").text() ?: throw ProviderFailure.Rejected("MonCash returned no access token")
        val lifetime = body.path("expires_in").asLong(DEFAULT_TOKEN_SECONDS).coerceAtLeast(TOKEN_MARGIN_SECONDS + 1)
        token = value to calendar.now().plusSeconds(lifetime - TOKEN_MARGIN_SECONDS)
        return value
    }

    private fun org.springframework.web.client.RestClient.post(path: String) =
        post().uri(path).contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)

    private fun readSuccess(answer: ProviderAnswer): JsonNode {
        if (!answer.isSuccess) throw rejected(answer)
        return runCatching { json.readTree(answer.body) }.getOrElse { throw ProviderFailure.Rejected("Unreadable response from MonCash") }
    }

    private fun rejected(answer: ProviderAnswer): ProviderFailure.Rejected {
        val body = runCatching { json.readTree(answer.body) }.getOrNull()
        val detail = mapOf("code" to (body?.path("error")?.text() ?: answer.status.toString()), "message" to body?.path("message")?.text())
        return ProviderFailure.Rejected("MonCash answered ${answer.status}", redaction.redact(detail))
    }

    private fun expiry(value: String): Instant? = runCatching { calendar.fromLocal(LocalDateTime.parse(value, EXPIRY)) }.getOrNull()

    companion object {
        const val TYPE = "moncash"
        const val CLIENT_ID = "client-id"
        const val CLIENT_SECRET = "client-secret"
        private const val SUCCESSFUL = "successful"
        private const val FAILED = "failed"
        private const val UNAUTHORIZED = 401
        private val SERVER_ERRORS = 500..599
        private const val ORDER_ID_PARAMETER = "orderId"
        private const val TRANSACTION_ID_PARAMETER = "transactionId"
        private const val DEFAULT_TOKEN_SECONDS = 59L
        private const val TOKEN_MARGIN_SECONDS = 10L
        private val EXPIRY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS")

        val DESCRIPTOR =
            ProviderDescriptor(
                displayName = "MonCash",
                capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
                nextActions = setOf(NextAction.Type.REDIRECT),
                payerRequired = false,
                returnUrlRequired = true,
                idempotentCreation = false,
            )
    }
}

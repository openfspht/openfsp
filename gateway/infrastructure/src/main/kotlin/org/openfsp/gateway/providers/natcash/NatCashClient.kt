package org.openfsp.gateway.providers.natcash

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
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
import org.springframework.http.MediaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.time.Duration
import java.time.LocalDate
import java.util.UUID

class NatCashClient(
    override val id: String,
    override val limits: AmountLimits,
    settings: ProviderProperties.Provider,
    timeout: Duration,
    private val json: JsonMapper,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderOperations {
    override val descriptor = DESCRIPTOR

    private val payments = ProviderHttp(settings.baseUrl, timeout)
    private val queries = settings.secondaryUrl?.let { ProviderHttp(it, timeout) } ?: payments
    private val username = settings.credential(USERNAME)
    private val password = settings.credential(PASSWORD)
    private val partnerCode = settings.credential(PARTNER_CODE)
    private val signatures = NatCashSignatures(settings.credential(PRIVATE_KEY), settings.credential(FUNCTION_CODE))
    private val redaction = SecretRedaction(listOf(username, password, settings.credential(PRIVATE_KEY), settings.credential(FUNCTION_CODE)))

    override fun create(request: ProviderPaymentRequest): ProviderPayment {
        val requestId = UUID.randomUUID().toString()
        val timestamp = calendar.now().toEpochMilli()
        val amount = amounts.toNumber(request.amount)
        val body =
            buildMap {
                put("requestId", requestId)
                put("partnerCode", partnerCode)
                put("username", username)
                put("password", password)
                put("callbackUrl", request.callbackUrl.toString())
                put("timestamp", timestamp)
                put("orderNumber", request.orderId)
                put("amount", amount)
                put("signature", signatures.forPayment(partnerCode, username, password, timestamp, requestId, request.orderId, amount.toPlainString()))
                request.payerPhoneNumber?.let { put("msisdn", it.removePrefix("+")) }
                request.locale?.let { put("language", it) }
            }
        val answer = payments.exchange { post().uri("/credential").contentType(MediaType.APPLICATION_JSON).body(body) }
        val result = read(answer)
        if (result.path("status").asInt(-1) != SUCCESS) throw rejected(result, answer)
        val url = result.path("url").text() ?: throw ProviderFailure.Rejected("NatCash returned no payment URL")
        return ProviderPayment(ProviderReport(request.orderId, null, PaymentStatus.PENDING, null), NextAction.redirect(url, null), null)
    }

    override fun lookup(orderId: String): ProviderReport {
        val requestId = UUID.randomUUID().toString()
        val body =
            mapOf(
                "requestId" to requestId,
                "username" to username,
                "password" to password,
                "partnerCode" to partnerCode,
                "orderNumber" to orderId,
                "signature" to signatures.forQuery(partnerCode, username, password, orderId, requestId),
            )
        val answer = queries.exchange { post().uri("/merchant/checkTransaction").contentType(MediaType.APPLICATION_JSON).body(body) }
        val result = read(answer)
        if (result.path("status").asInt(-1) != SUCCESS) {
            if (result.path("code").text() in UNSETTLED_ERRORS) return ProviderReport(orderId, null, PaymentStatus.PENDING, null)
            throw rejected(result, answer)
        }
        val data = result.path("data")
        val code = data.path("responseCode").text().orEmpty()
        return ProviderReport(
            orderId = data.path("orderNumber").text() ?: orderId,
            providerReference = data.path("transId").text(),
            status = statusOf(code),
            amount = data.path("amount").text()?.replace(GROUPING, "")?.let(amounts::fromDecimal),
            failureReason = FailureReason.UNSPECIFIED.takeIf { statusOf(code) == PaymentStatus.FAILED },
            failureDetail = mapOf("code" to code, "message" to result.path("message").text()).takeIf { statusOf(code) == PaymentStatus.FAILED },
        )
    }

    override fun verifiedCallback(request: CallbackRequest): ProviderReport? {
        val fields = callbackFields(request)
        val orderNumber = fields[ORDER_NUMBER] ?: return null
        val code = fields[CODE] ?: return null
        val signature = fields[SIGNATURE] ?: return null
        if (!signatures.matches(signatures.forCallback(orderNumber, code), signature)) return null
        val status = statusOf(code)
        return ProviderReport(
            orderId = orderNumber,
            providerReference = fields[TRANS_ID],
            status = status,
            amount = null,
            failureReason = FailureReason.UNSPECIFIED.takeIf { status == PaymentStatus.FAILED },
            failureDetail = mapOf("code" to code).takeIf { status == PaymentStatus.FAILED },
        )
    }

    override fun unsignedCallback(request: CallbackRequest): ProviderReport? = null

    override fun statement(day: LocalDate): ProviderStatement = throw UnsupportedOperationException("NatCash offers no statement")

    private fun callbackFields(request: CallbackRequest): Map<String, String> {
        if (request.parameters.containsKey(ORDER_NUMBER)) return request.parameters
        val node = runCatching { json.readTree(request.body) }.getOrNull() ?: return emptyMap()
        return listOf(CODE, TRANS_ID, ORDER_NUMBER, SIGNATURE).mapNotNull { name -> node.path(name).text()?.let { name to it } }.toMap()
    }

    private fun statusOf(code: String): PaymentStatus =
        when (code) {
            "1" -> PaymentStatus.SUCCEEDED
            "-1" -> PaymentStatus.FAILED
            else -> PaymentStatus.PENDING
        }

    private fun read(answer: ProviderAnswer): JsonNode {
        if (answer.status >= SERVER_ERROR) throw ProviderFailure.Rejected("NatCash answered ${answer.status}")
        return runCatching { json.readTree(answer.body) }.getOrElse { throw ProviderFailure.Rejected("Unreadable response from NatCash") }
    }

    private fun rejected(
        result: JsonNode,
        answer: ProviderAnswer,
    ) = ProviderFailure.Rejected(
        "NatCash answered ${answer.status}",
        redaction.redact(mapOf("code" to result.path("code").text(), "message" to result.path("message").text())),
    )

    companion object {
        const val TYPE = "natcash"
        const val USERNAME = "username"
        const val PASSWORD = "password"
        const val PARTNER_CODE = "partner-code"
        const val PRIVATE_KEY = "private-key"
        const val FUNCTION_CODE = "function-code"
        private const val SUCCESS = 0
        private const val SERVER_ERROR = 500
        private const val CODE = "code"
        private const val TRANS_ID = "transId"
        private const val ORDER_NUMBER = "orderNumber"
        private const val SIGNATURE = "signature"
        private val UNSETTLED_ERRORS = setOf("ERR_TRANSACTION_NOT_FOUND", "ERR_TRANSACTION_EXPIRED")
        private val GROUPING = Regex("[,\\s]")

        val DESCRIPTOR =
            ProviderDescriptor(
                displayName = "NatCash",
                capabilities =
                    setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.WEBHOOKS_VERIFY, ProviderDescriptor.PAYMENT_URL),
                nextActions = setOf(NextAction.Type.REDIRECT),
                payerRequired = false,
                locales = setOf("en", "fr", "ht"),
                idempotentCreation = false,
            )
    }
}

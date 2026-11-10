package org.openfsp.gateway.providers

import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPayment
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatement
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientResponseException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class SimulatedOperatorClient(
    override val id: String,
    override val descriptor: ProviderDescriptor,
    override val limits: AmountLimits,
    private val settings: ProviderProperties.Provider,
    timeout: Duration,
    private val json: JsonMapper,
    private val amounts: DecimalAmounts,
    private val calendar: BusinessCalendar,
) : ProviderOperations {

    private val client =
        RestClient
            .builder()
            .baseUrl(settings.baseUrl.toString())
            .requestFactory(
                JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(timeout).build()).apply { setReadTimeout(timeout) },
            ).defaultHeader("Authorization", "Bearer ${settings.apiKey}")
            .defaultStatusHandler(HttpStatusCode::isError) { _, _ -> }
            .build()

    override fun create(request: ProviderPaymentRequest): ProviderPayment {
        val body =
            mapOf(
                "order_id" to request.orderId,
                "amount" to amounts.toDecimal(request.amount),
                "msisdn" to request.payerPhoneNumber?.removePrefix("+"),
                "description" to request.description,
                "callback_url" to request.callbackUrl.toString(),
            )
        val payment = call { client.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).body(body) }
        val receivedAt = calendar.now()
        val nextAction =
            when (payment.approval) {
                "PAYER_APPROVAL" -> NextAction.payerApproval
                "REDIRECT" ->
                    NextAction.redirect(
                        payment.checkoutUrl ?: throw ProviderFailure.Rejected("Redirect without checkout URL from the provider"),
                        payment.expiresIn?.let { receivedAt.plusSeconds(it) },
                    )
                else -> throw ProviderFailure.Rejected("Unknown approval type from the provider")
            }
        val report = payment.toReport() ?: throw ProviderFailure.Rejected("Unknown payment status from the provider")
        return ProviderPayment(report, nextAction, payment.expiresIn?.let { receivedAt.plusSeconds(it) })
    }

    override fun lookup(orderId: String): ProviderReport {
        val payment = call { client.get().uri("/payments/by-order/{orderId}", orderId) }
        return payment.toReport() ?: throw ProviderFailure.Rejected("Unknown payment status from the provider")
    }

    override fun verifiedCallback(
        headers: (String) -> String?,
        body: ByteArray,
    ): ProviderReport? {
        if (!descriptor.hasSignedCallbacks) return null
        val secret = settings.callbackSecret ?: return null
        val presented = headers(SIGNATURE_HEADER)?.removePrefix("sha256=") ?: return null
        if (!MessageDigest.isEqual(presented.toByteArray(), sign(secret, body).toByteArray())) return null
        val callback = runCatching { json.readValue<OperatorCallback>(body) }.getOrNull() ?: return null
        return OperatorPayment(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason, "PAYER_APPROVAL", null).toReport()
    }

    override fun unsignedCallback(body: ByteArray): ProviderReport? {
        if (!descriptor.hasPaymentUrl) return null
        val callback = runCatching { json.readValue<OperatorCallback>(body) }.getOrNull() ?: return null
        return OperatorPayment(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason, "PAYER_APPROVAL", null).toReport()
    }

    override fun statement(day: LocalDate): ProviderStatement {
        val statement = json.readValue<OperatorStatement>(fetch { client.get().uri("/statements/{day}", day) })
        return ProviderStatement(
            businessDate = statement.businessDate,
            closed = statement.closed,
            lines =
                statement.entries.map { line ->
                    ProviderStatementLine(
                        providerReference = line.reference,
                        orderId = line.orderId,
                        type = line.type.lowercase().takeIf { it in LINE_TYPES } ?: "adjustment",
                        amount = amounts.fromDecimal(line.amount),
                        bookedAt = calendar.fromLocal(LocalDateTime.parse(line.bookedAt)),
                    )
                },
        )
    }

    private fun call(request: () -> RestClient.RequestHeadersSpec<*>): OperatorPayment =
        runCatching { json.readValue<OperatorPayment>(fetch(request)) }.getOrElse {
            if (it is ProviderFailure) throw it
            throw ProviderFailure.Rejected("Unreadable response from the provider")
        }

    private fun fetch(request: () -> RestClient.RequestHeadersSpec<*>): ByteArray {
        val response =
            try {
                request().retrieve().toEntity(ByteArray::class.java)
            } catch (exception: ResourceAccessException) {
                throw when (exception.cause) {
                    is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ProviderFailure.Unavailable(exception)
                    else -> ProviderFailure.Timeout(exception)
                }
            } catch (exception: RestClientResponseException) {
                throw ProviderFailure.Rejected("Unexpected response from the provider: ${exception.statusCode.value()}")
            }
        val body = response.body ?: throw ProviderFailure.Rejected("Empty response from the provider")
        if (response.statusCode.isError) {
            val detail = runCatching { json.readValue<Map<String, Any?>>(body) }.getOrNull()
            throw ProviderFailure.Rejected("The provider answered ${response.statusCode.value()}", detail?.filterKeys { it in RELAYED }?.mapValues { redact(it.value) })
        }
        return body
    }

    private fun OperatorPayment.toReport(): ProviderReport? {
        val status =
            when (status) {
                "PENDING" -> PaymentStatus.PENDING
                "SUCCESSFUL" -> PaymentStatus.SUCCEEDED
                "FAILED" -> PaymentStatus.FAILED
                "EXPIRED" -> PaymentStatus.EXPIRED
                else -> return null
            }
        val failureReason =
            if (status != PaymentStatus.FAILED) {
                null
            } else {
                when (reason) {
                    "DECLINED" -> FailureReason.DECLINED
                    "INSUFFICIENT_FUNDS" -> FailureReason.INSUFFICIENT_FUNDS
                    else -> FailureReason.UNSPECIFIED
                }
            }
        return ProviderReport(
            orderId = orderId,
            providerReference = paymentId,
            status = status,
            amount = amounts.fromDecimal(amount),
            failureReason = failureReason,
            failureDetail = if (status == PaymentStatus.FAILED) mapOf("code" to reason, "message" to reason) else null,
        )
    }

    private fun redact(value: Any?): Any? =
        (value as? String)?.let { text -> listOfNotNull(settings.apiKey, settings.callbackSecret).fold(text) { acc, secret -> acc.replace(secret, REDACTED) } } ?: value

    private fun sign(
        secret: String,
        body: ByteArray,
    ): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(body))
    }

    private data class OperatorPayment(
        val paymentId: String,
        val orderId: String,
        val status: String,
        val amount: String,
        val reason: String?,
        val approval: String,
        val expiresIn: Long?,
        val checkoutUrl: String? = null,
    )

    private data class OperatorStatementEntry(
        val reference: String,
        val orderId: String?,
        val type: String,
        val amount: String,
        val bookedAt: String,
    )

    private data class OperatorStatement(
        val businessDate: LocalDate,
        val closed: Boolean,
        val entries: List<OperatorStatementEntry>,
    )

    private data class OperatorCallback(
        val paymentId: String,
        val orderId: String,
        val status: String,
        val amount: String,
        val reason: String?,
    )

    companion object {
        private const val SIGNATURE_HEADER = "X-Mock-Signature"
        private const val REDACTED = "[redacted]"
        private val RELAYED = setOf("code", "message")
        private val LINE_TYPES = setOf("payment", "reversal", "fee", "adjustment")
    }
}

package org.openfsp.gateway.provider

import org.openfsp.gateway.GatewayProperties
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
import java.time.Clock
import java.time.Duration
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AlphaAdapter(
    private val settings: GatewayProperties.ProviderSettings,
    timeout: Duration,
    private val json: JsonMapper,
    private val clock: Clock,
) : ProviderAdapter {
    override val id = ID
    override val descriptor =
        ProviderDescriptor(
            displayName = "Mock Alpha",
            capabilities = setOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.WEBHOOKS_VERIFY),
            nextActions = setOf(NextAction.Type.PAYER_APPROVAL),
            payerRequired = true,
        )

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
                "amount" to Amounts.toDecimal(request.amount),
                "msisdn" to request.payerPhoneNumber?.removePrefix("+"),
                "description" to request.description,
                "callback_url" to request.callbackUrl.toString(),
            )
        val payment = call { client.post().uri("/payments").contentType(MediaType.APPLICATION_JSON).body(body) }
        val receivedAt = clock.instant()
        val nextAction =
            when (payment.approval) {
                "PAYER_APPROVAL" -> NextAction.payerApproval
                else -> throw ProviderException.Error("Unknown approval type from the provider")
            }
        val report = payment.toReport() ?: throw ProviderException.Error("Unknown payment status from the provider")
        return ProviderPayment(report, nextAction, payment.expiresIn?.let { receivedAt.plusSeconds(it) })
    }

    override fun lookup(orderId: String): ProviderReport {
        val payment = call { client.get().uri("/payments/by-order/{orderId}", orderId) }
        return payment.toReport() ?: throw ProviderException.Error("Unknown payment status from the provider")
    }

    override fun verifiedCallback(
        headers: (String) -> String?,
        body: ByteArray,
    ): ProviderReport? {
        val presented = headers(SIGNATURE_HEADER)?.removePrefix("sha256=") ?: return null
        if (!MessageDigest.isEqual(presented.toByteArray(), sign(body).toByteArray())) return null
        val callback = runCatching { json.readValue<AlphaCallback>(body) }.getOrNull() ?: return null
        return AlphaPayment(callback.paymentId, callback.orderId, callback.status, callback.amount, callback.reason, "PAYER_APPROVAL", null).toReport()
    }

    private fun call(request: () -> RestClient.RequestHeadersSpec<*>): AlphaPayment {
        val response =
            try {
                request().retrieve().toEntity(ByteArray::class.java)
            } catch (exception: ResourceAccessException) {
                throw when (exception.cause) {
                    is ConnectException, is UnknownHostException, is HttpConnectTimeoutException -> ProviderException.Unavailable(exception)
                    else -> ProviderException.Timeout(exception)
                }
            } catch (exception: RestClientResponseException) {
                throw ProviderException.Error("Unexpected response from the provider: ${exception.statusCode.value()}")
            }
        val body = response.body ?: throw ProviderException.Error("Empty response from the provider")
        if (response.statusCode.isError) {
            val detail = runCatching { json.readValue<Map<String, Any?>>(body) }.getOrNull()
            throw ProviderException.Error("The provider answered ${response.statusCode.value()}", detail?.filterKeys { it in setOf("code", "message") })
        }
        return runCatching { json.readValue<AlphaPayment>(body) }.getOrElse { throw ProviderException.Error("Unreadable response from the provider") }
    }

    private fun AlphaPayment.toReport(): ProviderReport? {
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
            amount = Amounts.fromDecimal(amount),
            failureReason = failureReason,
            failureDetail = if (status == PaymentStatus.FAILED) mapOf("code" to reason, "message" to reason) else null,
        )
    }

    private fun sign(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(settings.callbackSecret.toByteArray(), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(body))
    }

    private data class AlphaPayment(
        val paymentId: String,
        val orderId: String,
        val status: String,
        val amount: String,
        val reason: String?,
        val approval: String,
        val expiresIn: Long?,
    )

    private data class AlphaCallback(
        val paymentId: String,
        val orderId: String,
        val status: String,
        val amount: String,
        val reason: String?,
    )

    companion object {
        const val ID = "mock_alpha"
        private const val SIGNATURE_HEADER = "X-Alpha-Signature"
    }
}

package org.openfsp.gateway.providers

import org.junit.jupiter.api.AfterEach
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.providers.natcash.NatCashClient
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import tools.jackson.databind.json.JsonMapper
import java.net.URI
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.HexFormat
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NatCashClientTest {
    private val operator = RecordedOperator()
    private val json = JsonMapper.builder().build()
    private val now = Instant.parse("2026-11-09T15:00:00Z")
    private val credentials =
        mapOf(
            NatCashClient.USERNAME to "merchant-user",
            NatCashClient.PASSWORD to "merchant-pass",
            NatCashClient.PARTNER_CODE to "PARTNER",
            NatCashClient.PRIVATE_KEY to "private-key-for-tests",
            NatCashClient.FUNCTION_CODE to "function-code-for-tests",
        )
    private val client =
        NatCashClient(
            "natcash",
            AmountLimits(),
            ProviderProperties.Provider(type = NatCashClient.TYPE, baseUrl = operator.url.resolve("/api/online-payment"), credentials = credentials),
            Duration.ofSeconds(2),
            json,
            DecimalAmounts(),
            BusinessCalendar(Clock.fixed(now, ZoneOffset.UTC)),
        )

    @AfterEach
    fun stop() = operator.close()

    private fun sha256(value: String) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray()))

    private fun hmac(
        key: String,
        value: String,
    ) = HexFormat.of().formatHex(Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }.doFinal(value.toByteArray()))

    @Test
    fun `signs a payment request as the documentation describes and redirects to the web view`() {
        operator.on("/api/online-payment/credential", 200, """{"code":"MSG_SUCCESS","expiredAt":300,"message":"Sikse","status":0,"url":"https://pay.natcom.example?token=abc"}""")
        val created =
            client.create(
                ProviderPaymentRequest("order1", Money.htg(1000), "+50940000911", null, URI("https://gw.example/callbacks/natcash/tok"), locale = "ht"),
            )
        assertEquals("https://pay.natcom.example?token=abc", created.nextAction.redirectUrl)
        assertNull(created.expiresAt)

        val sent = json.readTree(operator.requests.single().body)
        val requestId = sent.get("requestId").asString()
        val expected =
            hmac("private-key-for-tests", sha256("private-key-for-tests$requestId") + "PARTNERmerchant-usermerchant-pass${now.toEpochMilli()}${requestId}order110")
        assertEquals(expected, sent.get("signature").asString())
        assertEquals("50940000911", sent.get("msisdn").asString())
        assertEquals("ht", sent.get("language").asString())
        assertEquals("https://gw.example/callbacks/natcash/tok", sent.get("callbackUrl").asString())
    }

    @Test
    fun `maps transaction results, including grouped amounts and numeric codes`() {
        operator.on(
            "/api/online-payment/merchant/checkTransaction",
            200,
            """{"status":0,"code":"MSG_SUCCESS","message":"Sikse","data":{"amount":"3,162.00","orderNumber":"order1","responseCode":1,"toPhone":"50940000911","transId":"174453"}}""",
        )
        operator.on("/api/online-payment/merchant/checkTransaction", 200, """{"status":0,"code":"MSG_SUCCESS","message":"Sikse","data":{"amount":"20","orderNumber":"order1","responseCode":"-1","transId":"174453"}}""")
        operator.on("/api/online-payment/merchant/checkTransaction", 200, """{"status":0,"code":"MSG_SUCCESS","message":"Sikse","data":{"amount":"20","orderNumber":"order1","responseCode":"-3"}}""")
        operator.on("/api/online-payment/merchant/checkTransaction", 200, """{"status":101,"code":"ERR_TRANSACTION_NOT_FOUND","message":"Transaction not found"}""")

        val succeeded = client.lookup("order1")
        assertEquals(PaymentStatus.SUCCEEDED, succeeded.status)
        assertEquals(Money.htg(316_200), succeeded.amount)
        assertEquals("174453", succeeded.providerReference)
        assertEquals(FailureReason.UNSPECIFIED, client.lookup("order1").failureReason)
        assertEquals(PaymentStatus.PENDING, client.lookup("order1").status)
        assertEquals(PaymentStatus.PENDING, client.lookup("order1").status)
    }

    @Test
    fun `accepts a callback only with the function code signature`() {
        val signature = hmac("function-code-for-tests", sha256("function-code-for-testsorder1") + "order11")
        val signed = CallbackRequest({ null }, mapOf("orderNumber" to "order1", "code" to "1", "transId" to "174453", "signature" to signature), ByteArray(0))
        assertEquals(PaymentStatus.SUCCEEDED, client.verifiedCallback(signed)!!.status)
        val forged = CallbackRequest({ null }, mapOf("orderNumber" to "order1", "code" to "1", "signature" to "00"), ByteArray(0))
        assertNull(client.verifiedCallback(forged))
    }
}

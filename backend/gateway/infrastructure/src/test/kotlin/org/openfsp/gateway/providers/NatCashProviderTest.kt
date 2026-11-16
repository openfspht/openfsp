package org.openfsp.gateway.providers

import org.junit.jupiter.api.AfterEach
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.providers.natcash.NatCashProviderFactory
import org.openfsp.gateway.providers.natcash.NatCashSettings
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
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class NatCashProviderTest {
    private val operator = RecordedOperator()
    private val json = JsonMapper.builder().build()
    private val now = Instant.parse("2026-11-09T15:00:00Z")
    private val credentials =
        mapOf(
            NatCashSettings.USERNAME to "merchant-user",
            NatCashSettings.PASSWORD to "merchant-pass",
            NatCashSettings.PARTNER_CODE to "PARTNER",
            NatCashSettings.PRIVATE_KEY to "private-key-for-tests",
            NatCashSettings.FUNCTION_CODE to "function-code-for-tests",
        )
    private val client = client(credentials)

    private fun client(
        credentials: Map<String, String>,
        timeout: Duration = Duration.ofSeconds(2),
    ) = NatCashProviderFactory(
            ProviderHttpFactory(ProviderProperties(providerTimeout = timeout), ProviderJson()),
            ProviderJson(),
            DecimalAmounts(),
            BusinessCalendar(Clock.fixed(now, ZoneOffset.UTC)),
        ).create(
            ProviderSetup(
                "natcash",
                ProviderProperties.Provider(type = NatCashProviderFactory.TYPE, baseUrl = operator.url.resolve("/api/online-payment"), credentials = credentials),
                AmountLimits(),
                null,
            ),
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

    @Test
    fun `an error echoing the partner code or any credential is redacted whatever its length`() {
        val shortCredentials = credentials + (NatCashSettings.PARTNER_CODE to "P7") + (NatCashSettings.PASSWORD to "pw")
        operator.on(
            "/api/online-payment/credential",
            200,
            """{"status":1,"code":"ERR_AUTH","message":"partner P7 refused merchant-user with pw and private-key-for-tests"}""",
        )
        val failure =
            assertFailsWith<ProviderFailure.Rejected> {
                client(shortCredentials).create(ProviderPaymentRequest("order1", Money.htg(1000), "+50940000911", null, URI("https://gw.example/callbacks/natcash/tok")))
            }
        assertEquals("partner [REDACTED] refused [REDACTED] with [REDACTED] and [REDACTED]", failure.detail!!["message"])
    }

    private val check = "/api/online-payment/merchant/checkTransaction"
    private val success = """{"status":0,"code":"MSG_SUCCESS","message":"Sikse","data":{"amount":"20","orderNumber":"order1","responseCode":"1","transId":"174453"}}"""

    @Test
    fun `a business result on a 4xx is never accepted`() {
        operator.on(check, 401, success)
        operator.on(check, 403, """{"status":0,"code":"ERR_FORBIDDEN","message":"merchant-pass refused"}""")

        assertEquals("NatCash answered 401", assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }.message)
        val forbidden = assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }
        assertEquals("ERR_FORBIDDEN", forbidden.detail!!["code"])
        assertEquals("[REDACTED] refused", forbidden.detail!!["message"])
    }

    @Test
    fun `a 429 keeps the Retry-After of NatCash`() {
        operator.on(check, 429, "", mapOf("Retry-After" to "12"))
        assertEquals(Duration.ofSeconds(12), assertFailsWith<ProviderFailure.Unavailable> { client.lookup("order1") }.retryAfter)
    }

    @Test
    fun `an HTML page, a truncated body or a success without data is an invalid format`() {
        operator.on(check, 200, "<html><body>Maintenance</body></html>")
        operator.on(check, 200, success.take(40))
        operator.on(check, 200, """{"status":0,"code":"MSG_SUCCESS"}""")
        operator.on(check, 200, """{"code":"MSG_SUCCESS"}""")

        repeat(4) { assertEquals("invalid_format", assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }.detail!!["code"]) }
    }

    @Test
    fun `a business refusal differs from an HTTP failure and keeps the operator code`() {
        operator.on(check, 200, """{"status":1,"code":"ERR_UNAUTHORIZED","message":"bad signature"}""")
        operator.on(check, 503, "<html>Service Unavailable</html>")

        val refused = assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }
        val unavailable = assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }

        assertEquals("NatCash refused the request", refused.message)
        assertEquals("ERR_UNAUTHORIZED", refused.detail!!["code"])
        assertEquals("NatCash answered 503", unavailable.message)
        assertEquals("503", unavailable.detail!!["code"])
    }

    @Test
    fun `a timeout is reported as such`() {
        operator.on(check, 200, success, delay = Duration.ofMillis(800))
        assertFailsWith<ProviderFailure.Timeout> { client(credentials, Duration.ofMillis(200)).lookup("order1") }
    }
}

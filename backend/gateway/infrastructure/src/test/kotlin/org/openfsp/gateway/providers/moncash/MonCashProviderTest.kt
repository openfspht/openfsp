package org.openfsp.gateway.providers.moncash

import org.junit.jupiter.api.AfterEach
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.providers.DecimalAmounts
import org.openfsp.gateway.providers.ProviderHttpFactory
import org.openfsp.gateway.providers.ProviderJson
import org.openfsp.gateway.providers.ProviderProperties
import org.openfsp.gateway.providers.ProviderSetup
import org.openfsp.gateway.providers.RecordedOperator
import org.openfsp.gateway.providers.moncash.MonCashProviderFactory
import org.openfsp.gateway.providers.moncash.MonCashSettings
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MonCashProviderTest {
    private val operator = RecordedOperator()
    private val clock = Clock.fixed(Instant.parse("2026-11-09T15:00:00Z"), ZoneOffset.UTC)
    private val client = client("client-123", "secret-456")

    private fun client(
        clientId: String,
        clientSecret: String,
        timeout: Duration = Duration.ofSeconds(2),
    ) = MonCashProviderFactory(
            ProviderHttpFactory(ProviderProperties(providerTimeout = timeout), ProviderJson()),
            ProviderJson(),
            DecimalAmounts(),
            BusinessCalendar(clock),
        ).create(
            ProviderSetup(
                "moncash",
                ProviderProperties.Provider(
                    type = MonCashProviderFactory.TYPE,
                    baseUrl = operator.url.resolve("/Api"),
                    secondaryUrl = URI("https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware"),
                    credentials = mapOf(MonCashSettings.CLIENT_ID to clientId, MonCashSettings.CLIENT_SECRET to clientSecret),
                    displayName = "MonCash (mock)",
                ),
                AmountLimits(),
                null,
            ),
        )

    init {
        operator.on("/Api/oauth/token", 200, """{"access_token":"tok-1","token_type":"bearer","expires_in":59,"scope":"read,write"}""")
    }

    @AfterEach
    fun stop() = operator.close()

    private fun request(amount: Long = 125_050) = ProviderPaymentRequest("order1", Money.htg(amount), null, null, URI("https://gw.example/callbacks/moncash"))

    @Test
    fun `a configured display name replaces the operator name`() {
        assertEquals("MonCash (mock)", client.descriptor.displayName)
    }

    @Test
    fun `creates a payment with a client credentials token and redirects to the MonCash middleware`() {
        operator.on(
            "/Api/v1/CreatePayment",
            202,
            """{"path":"/v1/CreatePayment","payment_token":{"expired":"2026-11-09 10:10:00:000","created":"2026-11-09 10:00:00:000","token":"pt-789"},"status":202}""",
        )
        val created = client.create(request())

        val token = operator.requests.first()
        assertEquals("/Api/oauth/token", token.path)
        assertTrue(token.query!!.contains("grant_type=client_credentials"))
        assertEquals("Basic " + Base64.getEncoder().encodeToString("client-123:secret-456".toByteArray()), token.authorization)
        val creation = operator.requests.last()
        assertEquals("Bearer tok-1", creation.authorization)
        assertEquals("""{"amount":1250.5,"orderId":"order1"}""", creation.body)
        assertEquals(NextAction.Type.REDIRECT, created.nextAction.type)
        assertEquals("https://sandbox.moncashbutton.digicelgroup.com/Moncash-middleware/Payment/Redirect?token=pt-789", created.nextAction.redirectUrl)
        assertEquals(Instant.parse("2026-11-09T15:10:00Z"), created.expiresAt)
        assertNull(created.report.amount)
    }

    @Test
    fun `reads successful, failed and unknown payments with cost as the amount`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 200, """{"payment":{"reference":"order1","transaction_id":"12874820","cost":10,"message":"successful","payer":"50937007294"},"status":200}""")
        operator.on("/Api/v1/RetrieveOrderPayment", 200, """{"payment":{"reference":"order1","transaction_id":"12874820","cost":10,"message":"failed","payer":"50937007294"},"status":200}""")
        operator.on("/Api/v1/RetrieveOrderPayment", 404, """{"error":"Not Found","message":"Can't find this transaction","status":404}""")

        val succeeded = client.lookup("order1")
        assertEquals(PaymentStatus.SUCCEEDED, succeeded.status)
        assertEquals("12874820", succeeded.providerReference)
        assertEquals(Money.htg(1000), succeeded.amount)
        assertEquals(FailureReason.UNSPECIFIED, client.lookup("order1").failureReason)
        assertEquals(PaymentStatus.PENDING, client.lookup("order1").status)
        assertEquals(1, operator.requests.count { it.path == "/Api/oauth/token" })
    }

    @Test
    fun `finds the order of a payer returning with a transaction id`() {
        operator.on("/Api/v1/RetrieveTransactionPayment", 200, """{"payment":{"reference":"order1","transaction_id":"12874820","message":"successful"},"status":200}""")
        assertEquals("order1", client.returnedOrder(mapOf("transactionId" to "12874820")))
        assertEquals("order2", client.returnedOrder(mapOf("orderId" to "order2")))
    }

    @Test
    fun `credentials bound from environment variables are found under any separator`() {
        val provider = ProviderProperties.Provider(baseUrl = URI("https://x.example"), credentials = mapOf("client.id" to "a", "CLIENT_SECRET" to "b"))
        assertEquals("a", provider.credential(MonCashSettings.CLIENT_ID))
        assertEquals("b", provider.credential(MonCashSettings.CLIENT_SECRET))
    }

    @Test
    fun `an error echoing the client secret is redacted`() {
        operator.on("/Api/v1/CreatePayment", 400, """{"error":"Bad Request","message":"bad client secret-456","status":400}""")
        val failure = assertFailsWith<ProviderFailure.Rejected> { client.create(request()) }
        assertEquals("bad client [REDACTED]", failure.detail!!["message"])
    }

    @Test
    fun `an error echoing the access token or the basic credentials is redacted`() {
        val basic = Base64.getEncoder().encodeToString("client-123:secret-456".toByteArray())
        operator.on("/Api/v1/RetrieveOrderPayment", 500, """{"error":"Internal","message":"token tok-1 issued for Basic $basic","status":500}""")
        val failure = assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }
        assertEquals("token [REDACTED] issued for Basic [REDACTED]", failure.detail!!["message"])
    }

    @Test
    fun `an error echoing a short client id or secret is redacted`() {
        operator.on("/Api/v1/CreatePayment", 400, """{"error":"Bad Request","message":"bad client c1 with s2","status":400}""")
        val failure = assertFailsWith<ProviderFailure.Rejected> { client("c1", "s2").create(request()) }
        assertEquals("bad client [REDACTED] with [REDACTED]", failure.detail!!["message"])
    }

    @Test
    fun `only the MonCash not found document reads as no payment`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 404, """{"error":"Not Found","message":"Can't find this transaction","status":404}""")
        assertEquals(PaymentStatus.PENDING, client.lookup("order1").status)
    }

    @Test
    fun `a persistent 401 after renewing the token is an error, not an absent payment`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 401, """{"error":"unauthorized","message":"Full authentication is required"}""")

        val failure = assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }

        assertEquals("unauthorized", failure.detail!!["code"])
        assertEquals(2, operator.requests.count { it.path == "/Api/oauth/token" })
        assertEquals(2, operator.requests.count { it.path == "/Api/v1/RetrieveOrderPayment" })
    }

    @Test
    fun `a 403, an HTML 404 or an empty 404 is an error, not an absent payment`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 403, """{"error":"Forbidden","message":"Access denied"}""")
        operator.on("/Api/v1/RetrieveOrderPayment", 404, "<html><body>Not Found</body></html>")
        operator.on("/Api/v1/RetrieveOrderPayment", 404, "{}")

        assertEquals("Forbidden", assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }.detail!!["code"])
        assertEquals("404", assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }.detail!!["code"])
        assertEquals("404", assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }.detail!!["code"])
    }

    @Test
    fun `a 429 keeps the Retry-After of MonCash`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 429, """{"error":"Too Many Requests"}""", mapOf("Retry-After" to "30"))

        val failure = assertFailsWith<ProviderFailure.Unavailable> { client.lookup("order1") }

        assertEquals(Duration.ofSeconds(30), failure.retryAfter)
        assertEquals("30", failure.toOpenFsp().retryAfter!!.seconds.toString())
    }

    @Test
    fun `a 5xx, an unreadable success and a timeout are errors`() {
        operator.on("/Api/v1/RetrieveOrderPayment", 502, "<html>Bad Gateway</html>")
        operator.on("/Api/v1/RetrieveOrderPayment", 200, """{"payment":{"reference":"order1","mess""")
        operator.on("/Api/v1/RetrieveOrderPayment", 200, "{}", delay = Duration.ofMillis(800))

        assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }
        assertFailsWith<ProviderFailure.Rejected> { client.lookup("order1") }
        assertFailsWith<ProviderFailure.Timeout> { client("client-123", "secret-456", Duration.ofMillis(200)).lookup("order1") }
    }
}

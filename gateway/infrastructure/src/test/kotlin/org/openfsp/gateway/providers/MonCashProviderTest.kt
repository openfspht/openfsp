package org.openfsp.gateway.providers

import org.junit.jupiter.api.AfterEach
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPaymentRequest
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
    private val client =
        MonCashProviderFactory(
            ProviderHttpFactory(ProviderProperties(providerTimeout = Duration.ofSeconds(2)), ProviderJson()),
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
                    credentials = mapOf(MonCashSettings.CLIENT_ID to "client-123", MonCashSettings.CLIENT_SECRET to "secret-456"),
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
        assertEquals("bad client [redacted]", failure.detail!!["message"])
    }
}

package org.openfsp.gateway.providers

import org.openfsp.gateway.FakeOpenFspOperator
import org.openfsp.gateway.FakeProfile
import org.openfsp.gateway.SignedCallback
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.CallbackRequest
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderPaymentRequest
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.providers.openfsp.OpenFspProviderFactory
import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.Money
import org.openfsp.protocol.NextAction
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.webhook.InMemoryNonceStore
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFspProviderTest {
    private class MovableClock(
        @Volatile var now: Instant,
    ) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun advance(duration: Duration) {
            now = now.plus(duration)
        }
    }

    private val operator =
        FakeOpenFspOperator(
            mapOf(
                "mock_alpha" to FakeProfile(listOf("payments", "payments.lookup", "webhooks.emit", "webhooks.verify", "payments.fee", "com.example.extra"), "payer_approval", payerRequired = true, locales = listOf("ht", "es")),
                "mock_plain" to FakeProfile(listOf("payments", "payments.lookup"), "payer_approval"),
                "mock_url" to FakeProfile(listOf("payments", "webhooks.per_payment_url"), "redirect"),
            ),
        )
    private val clock = MovableClock(Instant.now())
    private val json = ProviderJson()
    private val factory = OpenFspProviderFactory(ProviderHttpFactory(ProviderProperties(providerTimeout = Duration.ofSeconds(2)), json), json, clock, InMemoryNonceStore(clock))

    @AfterTest
    fun stop() = operator.close()

    private fun provider(
        id: String = "mock_alpha",
        baseUrl: String = operator.url(id),
        apiKey: String = "${id}_test_key",
    ): ProviderOperations = factory.create(ProviderSetup(id, ProviderProperties.Provider(baseUrl = URI(baseUrl), apiKey = apiKey), AmountLimits(), null))

    private fun request(orderId: String) =
        ProviderPaymentRequest(
            orderId = orderId,
            amount = Money.htg(125_000),
            payerPhoneNumber = "+50934567890",
            description = null,
            callbackUrl = URI("http://127.0.0.1:8080/callbacks/mock_alpha"),
            locale = "ht",
            returnUrl = URI("https://shop.example/orders/1"),
        )

    private fun SignedCallback.toRequest(
        target: String = "http://127.0.0.1:8080/callbacks/mock_alpha",
        method: String = "POST",
    ) = CallbackRequest({ name -> headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value }, emptyMap(), body.toByteArray(), method, URI(target))

    @Test
    fun `reads the capabilities of the operator and refreshes them once they expire`() {
        val alpha = provider()
        val descriptor = alpha.descriptor
        assertEquals("Fake mock_alpha", descriptor.displayName)
        assertEquals(setOf("payments", "payments.lookup"), descriptor.capabilities)
        assertTrue(descriptor.signedCallbacks && !descriptor.paymentUrl)
        assertEquals(setOf(NextAction.Type.PAYER_APPROVAL), descriptor.nextActions)
        assertTrue(descriptor.payerRequired && descriptor.idempotentCreation)
        assertEquals(setOf("ht"), descriptor.locales)
        alpha.descriptor
        assertEquals(1, operator.capabilityReads.get())
        clock.advance(Duration.ofMinutes(6))
        alpha.descriptor
        assertEquals(2, operator.capabilityReads.get())
    }

    @Test
    fun `the capabilities are kept for the max-age the operator announces`() {
        operator.capabilitiesCacheControl = "public, max-age=120"
        val alpha = provider()
        alpha.descriptor
        clock.advance(Duration.ofSeconds(119))
        alpha.descriptor
        assertEquals(1, operator.capabilityReads.get())
        clock.advance(Duration.ofSeconds(2))
        alpha.descriptor
        assertEquals(2, operator.capabilityReads.get())
    }

    @Test
    fun `a max-age above one hour is capped and an unreadable one keeps five minutes`() {
        operator.capabilitiesCacheControl = "max-age=86400"
        val alpha = provider()
        alpha.descriptor
        clock.advance(Duration.ofMinutes(59))
        alpha.descriptor
        assertEquals(1, operator.capabilityReads.get())
        clock.advance(Duration.ofMinutes(2))
        operator.capabilitiesCacheControl = "max-age=soon"
        alpha.descriptor
        assertEquals(2, operator.capabilityReads.get())
        clock.advance(Duration.ofMinutes(4))
        alpha.descriptor
        assertEquals(2, operator.capabilityReads.get())
        clock.advance(Duration.ofMinutes(2))
        alpha.descriptor
        assertEquals(3, operator.capabilityReads.get())
    }

    @Test
    fun `an operator whose capabilities cannot be read announces payments only`() {
        val unreachable = provider("mock_down", "http://127.0.0.1:9/mock_down")
        assertEquals(setOf("payments"), unreachable.descriptor.capabilities)
        assertEquals("mock_down", unreachable.descriptor.displayName)
    }

    @Test
    fun `creates the payment with a key derived from the order and sends the callback URL only to an operator that offers per-payment URLs`() {
        val payment = provider().create(request("order1").copy(expiresAt = Instant.parse("2026-11-16T12:30:00Z")))
        assertEquals(NextAction.Type.PAYER_APPROVAL, payment.nextAction.type)
        assertEquals("order1", payment.report.orderId)
        assertEquals("MA-order1", payment.report.providerReference)
        val (headers, body) = operator.requests.last()
        assertEquals("create-order1", headers["idempotency-key"])
        assertTrue(!body.contains("callback_url"))
        assertTrue(body.contains("\"locale\":\"ht\"") && body.contains("\"return_url\":\"https://shop.example/orders/1\""))
        assertTrue(body.contains("\"expires_at\":\"2026-11-16T12:30:00Z\""), body)

        provider("mock_plain").create(request("order2"))
        assertTrue(!operator.requests.last().second.contains("callback_url"))

        val redirected = provider("mock_url").create(request("order3"))
        assertTrue(operator.requests.last().second.contains("\"callback_url\":\"http://127.0.0.1:8080/callbacks/mock_alpha\""))
        assertEquals(NextAction.Type.REDIRECT, redirected.nextAction.type)
        assertEquals("https://fake.example/checkout/order3", redirected.nextAction.redirectUrl)
    }

    @Test
    fun `maps statuses and failure reasons one to one`() {
        val alpha = provider()
        val reported = { status: String, reason: String? ->
            val orderId = "order-$status-$reason"
            operator.statuses[orderId] = status
            reason?.let { operator.reasons[orderId] = it }
            alpha.lookup(orderId)
        }
        assertEquals(PaymentStatus.PENDING, reported("pending", null).status)
        assertEquals(PaymentStatus.SUCCEEDED, reported("succeeded", null).status)
        assertEquals(PaymentStatus.EXPIRED, reported("expired", null).status)
        assertEquals(PaymentStatus.CANCELED, reported("canceled", null).status)
        FailureReason.entries.forEach { reason -> assertEquals(reason, reported("failed", reason.wire).failureReason) }
        assertEquals(FailureReason.UNSPECIFIED, reported("failed", "bored").failureReason)
        assertEquals(FailureReason.UNSPECIFIED, reported("failed", null).failureReason)
        assertNull(reported("succeeded", "declined").failureReason)
        assertEquals(PaymentStatus.PENDING, reported("in_review", null).status)
        assertNull(reported("in_review", "declined").failureReason)
        assertFailsWith<ProviderFailure.Rejected> { alpha.lookup("unknown-order") }
    }

    @Test
    fun `secrets echoed by the operator are redacted from the failure and provider details`() {
        val alpha = provider()
        operator.statuses["order4"] = "failed"
        operator.reasons["order4"] = "declined"
        operator.details["order4"] = "Refused for mock_alpha_test_key"
        val detail = alpha.lookup("order4").failureDetail!!
        assertEquals("E42", detail["code"])
        assertEquals("Refused for ${ExchangeRedaction.MASK}", detail["message"])

        operator.failWith = 400
        operator.errorMessage = "Rejected request authorized with Bearer mock_alpha_test_key"
        val failure = assertFailsWith<ProviderFailure.Rejected> { alpha.create(request("order5")) }
        assertEquals("internal-error", failure.detail!!["code"])
        assertEquals("Rejected request authorized with Bearer ${ExchangeRedaction.MASK}", failure.detail!!["message"])
    }

    @Test
    fun `a short API key echoed by the operator is redacted`() {
        operator.failWith = 400
        operator.errorMessage = "Rejected key k9"
        val failure = assertFailsWith<ProviderFailure.Rejected> { provider(apiKey = "k9").create(request("order6")) }
        assertEquals("Rejected key ${ExchangeRedaction.MASK}", failure.detail!!["message"])
    }

    @Test
    fun `an operator 4xx is a rejection without effect while a 5xx or an in-progress conflict keeps the outcome unknown`() {
        val alpha = provider()
        operator.failWith = 422
        assertTrue(assertFailsWith<ProviderFailure.Rejected> { alpha.create(request("order10")) }.withoutEffect)
        operator.failWith = 500
        assertFalse(assertFailsWith<ProviderFailure.Rejected> { alpha.create(request("order11")) }.withoutEffect)
        operator.failWith = 409
        operator.errorEffect = "unknown"
        assertFalse(assertFailsWith<ProviderFailure.Rejected> { alpha.create(request("order12")) }.withoutEffect)
    }

    @Test
    fun `an operator still processing the same creation reads as in progress`() {
        operator.failWith = 409
        operator.errorEffect = "unknown"
        operator.errorType = "idempotency-request-in-progress"
        assertFailsWith<ProviderFailure.InProgress> { provider().create(request("order13")) }
    }

    @Test
    fun `a signed event is verified against the operator keys, once, for its method, target and body`() {
        val alpha = provider()
        val callback = operator.callback("order6", "succeeded")
        assertEquals(PaymentStatus.SUCCEEDED, alpha.verifiedCallback(callback.toRequest())!!.status)
        assertNull(alpha.verifiedCallback(callback.toRequest()))

        val other = operator.callback("order6", "succeeded")
        assertNull(alpha.verifiedCallback(other.copy(body = other.body.replace("125000", "125001")).toRequest()))
        assertNull(alpha.verifiedCallback(operator.callback("order6", "succeeded").toRequest("http://127.0.0.1:8080/callbacks/mock_other")))
        assertNull(alpha.verifiedCallback(operator.callback("order6", "succeeded").toRequest(method = "GET")))
        assertNull(alpha.unsignedCallback(operator.callback("order6", "succeeded").toRequest()))
        assertNull(provider("mock_plain").verifiedCallback(operator.callback("order6", "succeeded").toRequest()))
    }

    @Test
    fun `an unknown key refreshes the whole key set, a retired key still verifies and a removed one no longer does`() {
        val alpha = provider()
        assertEquals(PaymentStatus.SUCCEEDED, alpha.verifiedCallback(operator.callback("order7", "succeeded").toRequest())!!.status)
        operator.rotateKey()
        val withNewKey = operator.callback("order7", "succeeded")
        assertNull(alpha.verifiedCallback(withNewKey.toRequest()))
        clock.advance(Duration.ofSeconds(11))
        assertEquals(PaymentStatus.SUCCEEDED, alpha.verifiedCallback(operator.callback("order7", "succeeded").toRequest())!!.status)
        assertEquals(PaymentStatus.SUCCEEDED, alpha.verifiedCallback(operator.callback("order7", "succeeded", signingKey = 0).toRequest())!!.status)

        val withRemovedKey = operator.callback("order7", "succeeded", signingKey = 0)
        operator.rotateKey(retirePrevious = false)
        clock.advance(Duration.ofSeconds(11))
        assertEquals(PaymentStatus.SUCCEEDED, alpha.verifiedCallback(operator.callback("order7", "succeeded").toRequest())!!.status)
        assertNull(alpha.verifiedCallback(withRemovedKey.toRequest()))
    }

    @Test
    fun `an operator offering per-payment URLs signs its events, so an event is never read without signature`() {
        val url = provider("mock_url")
        assertTrue(url.descriptor.paymentUrl && url.descriptor.signedCallbacks)
        assertEquals(setOf("payments"), url.descriptor.capabilities)
        val event = operator.event("order8", "failed", reason = "payer_canceled", operator = "mock_url").toByteArray()
        assertNull(url.unsignedCallback(CallbackRequest({ null }, emptyMap(), event)))
        val signed = operator.callback("order8", "failed", reason = "payer_canceled", operator = "mock_url")
        assertEquals(FailureReason.PAYER_CANCELED, url.verifiedCallback(signed.toRequest("http://127.0.0.1:8080/callbacks/mock_url"))!!.failureReason)
    }

    @Test
    fun `reads the statement of the operator for a business day`() {
        val day = LocalDate.parse("2026-03-08")
        operator.statementLine("MA-order9", 125_000, "${day}T01:30:00")
        operator.statementLine(null, -500, "${day}T23:30:00")
        operator.statementClosed = true
        val statement = provider().statement(day)
        assertTrue(statement.closed)
        assertEquals(listOf("MA-order9", null), statement.lines.map { it.paymentReference })
        assertEquals(listOf("MA-order9", statement.lines.last().providerReference), statement.lines.map { it.transactionReference })
        assertEquals(Money.htg(-500), statement.lines.last().amount)
        assertEquals(Instant.parse("2026-03-08T06:30:00Z"), statement.lines.first().bookedAt)
    }
}

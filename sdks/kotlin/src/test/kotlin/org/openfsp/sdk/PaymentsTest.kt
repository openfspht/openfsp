package org.openfsp.sdk

import org.openfsp.protocol.Money as WireMoney
import org.openfsp.sdk.http.RetryPolicy
import org.openfsp.sdk.model.Money
import org.openfsp.sdk.model.PaymentRequest
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import org.openfsp.sdk.testing.Wire
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PaymentsTest {
    private val request = PaymentRequest("INV-184", Money.htg(1250), "moncash")
    private val clock = FakeClock()

    private fun openfsp(
        transport: FakeTransport,
        retryPolicy: RetryPolicy = RetryPolicy.DEFAULT,
    ) = OpenFsp(OpenFspConfig("https://server.example/openfsp", "ofsp_test_key", retryPolicy = retryPolicy), transport, clock)

    @Test
    fun `creates a payment with the idempotency key`() {
        val transport = FakeTransport(Responses.json(201, Wire.payment()))
        val payment = openfsp(transport).payments.create(request, "key-1")

        val sent = transport.requests.single()
        assertEquals("pay_test", payment.id)
        assertEquals("POST", sent.method)
        assertEquals("https://server.example/openfsp/v1/payments", sent.uri.toString())
        assertEquals("Bearer ofsp_test_key", sent.header("authorization"))
        assertEquals("key-1", sent.header("idempotency-key"))
        assertEquals("""{"reference":"INV-184","amount":{"amount":125000,"currency":"HTG"},"provider":"moncash"}""", String(sent.body!!))
    }

    @Test
    fun `an unknown status is a processing error that names the payment`() {
        val unknown = Responses.json(200, Wire.payment(status = "in_review"))
        val refused = assertFailsWith<UnknownPaymentStatusException> { openfsp(FakeTransport(unknown)).payments.get("pay_test") }

        assertEquals("unexpected-response", refused.code)
        assertEquals(listOf("pay_test", "INV-184", "in_review"), listOf(refused.paymentId, refused.reference, refused.unknownStatus))
    }

    @Test
    fun `a lost answer is handed back when the caller recovers on its own`() {
        val config = OpenFspConfig.builder().baseUrl("https://server.example").apiKey("k").retryPolicy(RetryPolicy.NONE).recoverLostAnswers(false).build()
        val transport = FakeTransport().enqueueFailure(TransportException("timed out"))

        assertFailsWith<TransportException> { OpenFsp(config, transport, clock).payments.create(request, "key-1") }
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `resends with the same key, waiting at least Retry-After`() {
        val transport =
            FakeTransport(
                Responses.problem(503, "provider-unavailable", mapOf("retryable" to true), mapOf("Retry-After" to "2")),
                Responses.json(201, Wire.payment()),
            )
        openfsp(transport).payments.create(request, "key-1")
        assertEquals(listOf("key-1", "key-1"), transport.requests.map { it.header("idempotency-key") })
        assertEquals(listOf(Duration.ofSeconds(2)), clock.sleeps)
    }

    @Test
    fun `resends rate-limited requests but no other client error`() {
        val limited = FakeTransport(Responses.problem(429, "rate-limited", mapOf("retryable" to true)), Responses.json(201, Wire.payment()))
        openfsp(limited).payments.create(request, "key-1")
        assertEquals(2, limited.requests.size)

        val reused = FakeTransport(Responses.problem(422, "idempotency-key-reused", mapOf("retryable" to true)))
        assertEquals("idempotency-key-reused", assertFailsWith<ApiException> { openfsp(reused).payments.create(request, "key-1") }.code)
        assertEquals(1, reused.requests.size)
    }

    @Test
    fun `reads the payment back when the outcome stays unknown`() {
        val lost = TransportException("connection reset")
        val transport = FakeTransport().enqueueFailure(lost, lost, lost).enqueue(Responses.json(200, mapOf("data" to listOf(Wire.payment()))))
        assertEquals("pay_test", openfsp(transport).payments.create(request, "key-1").id)
        assertEquals("reference=INV-184", transport.requests.last().uri.query)
    }

    @Test
    fun `keeps the original failure when the payment found differs`() {
        val other = Wire.payment().copy(amount = WireMoney.htg(5))
        val transport = FakeTransport().enqueueFailure(TransportException("reset")).enqueue(Responses.json(200, mapOf("data" to listOf(other))))
        assertFailsWith<TransportException> { openfsp(transport, RetryPolicy.NONE).payments.create(request, "key-1") }
    }

    @Test
    fun `does not look the payment up when nothing happened`() {
        val transport = FakeTransport(Responses.problem(422, "invalid-field", mapOf("errors" to listOf(mapOf("field" to "/amount", "code" to "too_large")))))
        val failure = assertFailsWith<ApiException> { openfsp(transport).payments.create(request, "key-1") }
        assertEquals(listOf(FieldError("/amount", "too_large")), failure.fieldErrors)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `refuses an invalid idempotency key or identifier before sending`() {
        val transport = FakeTransport()
        assertFailsWith<InvalidRequestException> { openfsp(transport).payments.create(request, "not valid") }
        assertFailsWith<InvalidRequestException> { openfsp(transport).payments.get("../admin") }
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `reads and finds payments`() {
        val transport =
            FakeTransport(
                Responses.json(200, Wire.payment()),
                Responses.json(200, Wire.payment(status = "succeeded")),
                Responses.problem(404, "not-found"),
                Responses.json(200, mapOf("data" to emptyList<Any>())),
            )
        val payments = openfsp(transport).payments
        assertTrue(payments.get("pay_test").isPending)
        assertTrue(payments.get("pay_test").isSuccessful)
        assertFailsWith<PaymentNotFoundException> { payments.get("pay_missing") }
        assertNull(payments.findByReference("INV-404"))
        assertEquals("GET /openfsp/v1/payments/pay_test", transport.requests[1].let { "${it.method} ${it.uri.path}" })
    }

    @Test
    fun `treats a malformed or redirected answer as unexpected`() {
        val transport =
            FakeTransport(
                Responses.json(200, mapOf("id" to "pay_test")),
                org.openfsp.sdk.http.TransportResponse(302, mapOf("Location" to "https://elsewhere.example"), ByteArray(0)),
            )
        val payments = openfsp(transport, RetryPolicy.NONE).payments
        assertEquals("unexpected-response", assertFailsWith<ApiException> { payments.get("pay_test") }.code)
        assertEquals(302, assertFailsWith<ApiException> { payments.get("pay_test") }.status)
    }

    @Test
    fun `resends after a gateway error page from a proxy`() {
        val page = { status: Int -> org.openfsp.sdk.http.TransportResponse(status, mapOf("Content-Type" to "text/html"), "<html>Bad Gateway</html>".toByteArray()) }
        val transport = FakeTransport(page(502), page(504), Responses.json(201, Wire.payment()))
        assertEquals("pay_test", openfsp(transport).payments.create(request, "key-1").id)
        assertEquals(listOf("key-1", "key-1", "key-1"), transport.requests.map { it.header("idempotency-key") })
    }

    @Test
    fun `resends while the server still processes the same key`() {
        val transport =
            FakeTransport(
                Responses.problem(409, "idempotency-request-in-progress", mapOf("retryable" to true, "effect" to "unknown")),
                Responses.json(201, Wire.payment()),
            )
        assertEquals("pay_test", openfsp(transport).payments.create(request, "key-1").id)
        assertEquals(listOf("key-1", "key-1"), transport.requests.map { it.header("idempotency-key") })
    }

    @Test
    fun `does not resend a server error marked not retryable`() {
        val transport = FakeTransport(Responses.problem(500, "internal-error"))
        assertEquals("internal-error", assertFailsWith<ApiException> { openfsp(transport).payments.create(request, "key-1") }.code)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `gives up when Retry-After exceeds the longest wait allowed`() {
        val transport = FakeTransport(Responses.problem(503, "provider-unavailable", mapOf("retryable" to true), mapOf("Retry-After" to "120")))
        val failure = assertFailsWith<ApiException> { openfsp(transport).payments.create(request, "key-1") }
        assertEquals(Duration.ofSeconds(120), failure.retryAfter)
        assertEquals(1, transport.requests.size)
        assertTrue(clock.sleeps.isEmpty())
    }

    @Test
    fun `reads the payment back after the provider timed out`() {
        val timeout = Responses.problem(504, "provider-timeout", mapOf("retryable" to true, "effect" to "unknown"))
        val transport = FakeTransport(timeout, timeout, timeout, Responses.json(200, mapOf("data" to listOf(Wire.payment()))))
        assertEquals("pay_test", openfsp(transport).payments.create(request, "key-1").id)
        assertEquals(4, transport.requests.size)
        assertEquals("GET", transport.requests.last().method)
    }
}

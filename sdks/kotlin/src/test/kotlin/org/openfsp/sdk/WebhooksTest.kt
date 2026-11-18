package org.openfsp.sdk

import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.webhook.WebhookAlgorithm
import org.openfsp.sdk.http.RetryPolicy
import org.openfsp.sdk.model.PaymentEventType
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import org.openfsp.sdk.testing.WebhookSigner
import org.openfsp.sdk.testing.Wire
import org.openfsp.sdk.webhooks.WebhookDelivery
import org.openfsp.sdk.webhooks.Webhooks
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WebhooksTest {
    private val now = Instant.parse("2026-11-15T12:00:00Z")
    private val url = "https://shop.example/openfsp/webhooks"
    private val signer = WebhookSigner.generate("key-1")
    private val clock = FakeClock(now)

    private fun webhooks() = Webhooks.withKeys(listOf(signer.key), clock = clock)

    private fun reason(block: () -> Unit) = assertFailsWith<InvalidSignatureException>(block = block).message

    @Test
    fun `returns the event of a signed delivery`() {
        val event = webhooks().verify(signer.sign(url, clock = clock))
        assertEquals(PaymentEventType.SUCCEEDED, event.type)
        assertTrue(event.payment.isSuccessful)
        assertEquals("INV-184", event.reference)
    }

    @Test
    fun `verifies against the public url behind a proxy`() {
        val delivery = signer.sign(url, clock = clock)
        val proxied = Webhooks.withKeys(listOf(signer.key), url = url, clock = clock)
        assertEquals("evt_test", proxied.verify(WebhookDelivery("POST", "http://10.0.0.4:3000/openfsp/webhooks", delivery.headers, delivery.body)).id)
    }

    @Test
    fun `rejects a replay and lets a forgery's nonce be used again`() {
        val receiver = webhooks()
        val delivery = signer.sign(url, clock = clock)
        receiver.verify(delivery)
        assertEquals("nonce already used", reason { receiver.verify(delivery) })

        val other = signer.sign(url, clock = clock)
        val forged = WebhookDelivery("POST", url, other.headers + ("Signature" to "sig1=:${"A".repeat(86)}==:"), other.body)
        assertEquals("invalid signature", reason { receiver.verify(forged) })
        receiver.verify(other)
    }

    @Test
    fun `rejects an expired delivery, a tampered body and an unknown key`() {
        val delivery = signer.sign(url, clock = FakeClock(now.minusSeconds(600)))
        assertEquals("signature expired", reason { webhooks().verify(delivery) })

        val fresh = signer.sign(url, clock = clock)
        assertEquals("Content-Digest does not match the body", reason { webhooks().verify(WebhookDelivery("POST", url, fresh.headers, fresh.body + 32)) })
        assertEquals("unknown keyid", reason { Webhooks.withKeys(emptyList(), clock = clock).verify(fresh) })
    }

    @Test
    fun `loads keys from the server and refreshes them at most every 10 seconds`() {
        val transport = FakeTransport(Responses.json(200, Wire.configuration()), Responses.json(200, Wire.configuration(listOf(signer.key))))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport, clock)

        reason { openfsp.webhooks.verify(signer.sign(url, clock = clock)) }
        reason { openfsp.webhooks.verify(signer.sign(url, clock = clock)) }
        assertEquals(1, transport.requests.size)

        clock.advance(Duration.ofSeconds(11))
        assertEquals("evt_test", openfsp.webhooks.verify(signer.sign(url, Wire.event(), clock)).id)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `reports keys it cannot load as unavailable, not as a bad signature`() {
        val transport = FakeTransport().enqueueFailure(TransportException("connection refused"))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k", retryPolicy = RetryPolicy.NONE), transport, clock)
        val failure = assertFailsWith<WebhookKeysUnavailableException> { openfsp.webhooks.verify(signer.sign(url, clock = clock)) }
        assertTrue(failure.cause is TransportException)
    }

    @Test
    fun `rejects a signed body it cannot read as a payment event`() {
        val unknownStatus = Wire.event().let { it.copy(data = it.data.copy(status = "refunded")) }
        assertEquals("the body is not a supported event", reason { webhooks().verify(signer.sign(url, unknownStatus, clock)) })
    }

    @Test
    fun `retries a failed key refresh after a short backoff`() {
        val transport = FakeTransport().enqueueFailure(TransportException("connection refused")).enqueue(Responses.json(200, Wire.configuration(listOf(signer.key))))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k", retryPolicy = RetryPolicy.NONE), transport, clock)

        assertFailsWith<WebhookKeysUnavailableException> { openfsp.webhooks.verify(signer.sign(url, clock = clock)) }
        assertFailsWith<WebhookKeysUnavailableException> { openfsp.webhooks.verify(signer.sign(url, clock = clock)) }
        assertEquals(1, transport.requests.size)

        clock.advance(Duration.ofSeconds(2))
        assertEquals("evt_test", openfsp.webhooks.verify(signer.sign(url, clock = clock)).id)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `verifies an ECDSA P-256 signature with an ES256 key`() {
        val p256 = WebhookSigner.generate("key-ec", WebhookAlgorithm.ECDSA_P256_SHA256)
        assertEquals("evt_test", Webhooks.withKeys(listOf(p256.key), clock = clock).verify(p256.sign(url, clock = clock)).id)
    }

    @Test
    fun `ignores a key whose alg or use contradicts its type`() {
        val delivery = signer.sign(url, clock = clock)
        listOf(signer.key.copy(algorithm = "ES256"), signer.key.copy(use = "enc"), signer.key.copy(y = signer.key.x)).forEach { key ->
            assertEquals("unknown keyid", reason { Webhooks.withKeys(listOf(key), clock = clock).verify(delivery) })
        }
    }

    @Test
    fun `verifies an event of an unknown type and reports it apart, to be acknowledged`() {
        val receiver = webhooks()
        val delivery = signer.sign(url, Wire.event().copy(id = "evt_refund", type = "refund.created"), clock)
        val unknown = assertFailsWith<UnknownEventTypeException> { receiver.verify(delivery) }
        assertEquals("evt_refund" to "refund.created", unknown.eventId to unknown.eventType)
        assertEquals("nonce already used", reason { receiver.verify(delivery) })

        val forged = WebhookDelivery("POST", url, delivery.headers + ("Signature" to "sig1=:${"A".repeat(86)}==:"), delivery.body)
        assertEquals("invalid signature", reason { webhooks().verify(forged) })
    }

    @Test
    fun `tells a terminal status from pending`() {
        assertEquals(listOf(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.EXPIRED, PaymentStatus.CANCELED), PaymentStatus.entries.filter { it.isTerminal })
    }
}

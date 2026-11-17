package org.openfsp.sdk

import org.openfsp.sdk.http.RetryPolicy
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
        val event = webhooks().verify(signer.sign(url, at = now))
        assertEquals("payment.succeeded", event.type)
        assertTrue(event.payment.isSuccessful)
        assertEquals("INV-184", event.reference)
    }

    @Test
    fun `verifies against the public url behind a proxy`() {
        val delivery = signer.sign(url, at = now)
        val proxied = Webhooks.withKeys(listOf(signer.key), url = url, clock = clock)
        assertEquals("evt_test", proxied.verify(WebhookDelivery("POST", "http://10.0.0.4:3000/openfsp/webhooks", delivery.headers, delivery.body)).id)
    }

    @Test
    fun `rejects a replay and lets a forgery's nonce be used again`() {
        val receiver = webhooks()
        val delivery = signer.sign(url, at = now)
        receiver.verify(delivery)
        assertEquals("nonce already used", reason { receiver.verify(delivery) })

        val other = signer.sign(url, at = now)
        val forged = WebhookDelivery("POST", url, other.headers + ("Signature" to "sig1=:${"A".repeat(86)}==:"), other.body)
        assertEquals("invalid signature", reason { receiver.verify(forged) })
        receiver.verify(other)
    }

    @Test
    fun `rejects an expired delivery, a tampered body and an unknown key`() {
        val delivery = signer.sign(url, at = now.minusSeconds(600))
        assertEquals("signature expired", reason { webhooks().verify(delivery) })

        val fresh = signer.sign(url, at = now)
        assertEquals("Content-Digest does not match the body", reason { webhooks().verify(WebhookDelivery("POST", url, fresh.headers, fresh.body + 32)) })
        assertEquals("unknown keyid", reason { Webhooks.withKeys(emptyList(), clock = clock).verify(fresh) })
    }

    @Test
    fun `loads keys from the server and refreshes them at most every 10 seconds`() {
        val transport = FakeTransport(Responses.json(200, Wire.configuration()), Responses.json(200, Wire.configuration(listOf(signer.key))))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k"), transport, clock)

        reason { openfsp.webhooks.verify(signer.sign(url, at = clock.now())) }
        reason { openfsp.webhooks.verify(signer.sign(url, at = clock.now())) }
        assertEquals(1, transport.requests.size)

        clock.advance(Duration.ofSeconds(11))
        assertEquals("evt_test", openfsp.webhooks.verify(signer.sign(url, Wire.event(), clock.now())).id)
        assertEquals(2, transport.requests.size)
    }

    @Test
    fun `reports keys it cannot load as unavailable, not as a bad signature`() {
        val transport = FakeTransport(TransportException("connection refused"))
        val openfsp = OpenFsp(OpenFspConfig("https://server.example", "k", retryPolicy = RetryPolicy.NONE), transport, clock)
        val failure = assertFailsWith<WebhookKeysUnavailableException> { openfsp.webhooks.verify(signer.sign(url, at = now)) }
        assertTrue(failure.cause is TransportException)
    }

    @Test
    fun `rejects a signed body it cannot read as a payment event`() {
        val unknownStatus = Wire.event().let { it.copy(data = it.data.copy(status = "refunded")) }
        assertEquals("the body is not a supported event", reason { webhooks().verify(signer.sign(url, unknownStatus, now)) })
    }
}

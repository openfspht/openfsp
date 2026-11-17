package org.openfsp.sdk

import org.openfsp.protocol.NextAction
import org.openfsp.sdk.testing.FakeClock
import org.openfsp.sdk.testing.FakeTransport
import org.openfsp.sdk.testing.Responses
import org.openfsp.sdk.testing.WebhookSigner
import org.openfsp.sdk.testing.Wire
import org.openfsp.sdk.webhooks.Webhooks
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TestingTest {
    @Test
    fun `gives a pending payment the next action the specification requires`() {
        assertEquals(NextAction.payerApproval, Wire.payment().nextAction)
        assertNull(Wire.payment(status = "succeeded").nextAction)
    }

    @Test
    fun `gives a created event no previous status`() {
        val created = Wire.event(Wire.payment())
        assertEquals("payment.created", created.type)
        assertNull(created.previousStatus)
        assertEquals("pending", Wire.event().previousStatus)
    }

    @Test
    fun `signs at the time of the clock it is given`() {
        val now = Instant.parse("2026-11-15T12:00:00Z")
        val signer = WebhookSigner.generate()
        val webhooks = Webhooks.withKeys(listOf(signer.key), clock = FakeClock(now))
        assertEquals("evt_test", webhooks.verify(signer.sign("https://shop.test/hooks", clock = FakeClock(now))).id)
        val stale = signer.sign("https://shop.test/hooks", clock = FakeClock(now.minusSeconds(600)))
        assertEquals("signature expired", assertFailsWith<InvalidSignatureException> { webhooks.verify(stale) }.message)
    }

    @Test
    fun `replays responses and failures in order`() {
        val transport = FakeTransport().enqueueFailure(TransportException("reset")).enqueue(Responses.json(200, Wire.payment()))
        val openfsp = OpenFsp(OpenFspConfig("https://server.test", "k"), transport, FakeClock())
        assertEquals("pay_test", openfsp.payments.get("pay_test").id)
        assertEquals(2, transport.requests.size)
    }

}

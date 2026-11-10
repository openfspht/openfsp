package org.openfsp.gateway.webhook

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.common.spi.stubs.RecordingOperatorSignals
import org.openfsp.gateway.webhook.spi.stubs.InMemoryDeliveries
import org.openfsp.gateway.webhook.spi.stubs.InMemoryEndpointHealth
import org.openfsp.gateway.webhook.spi.stubs.ScriptedTransport
import java.net.URI
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WebhookDeliveryTest {
    private val clock = TestClock()
    private val deliveries = InMemoryDeliveries()
    private val transport = ScriptedTransport()
    private val health = InMemoryEndpointHealth()
    private val signals = RecordingOperatorSignals()
    private val deliverer =
        WebhookDeliverer(deliveries, transport, health, DeliveryPolicy(), ImmediateTransactions(), signals, BusinessCalendar(clock), DeliveryLimits())
    private val shop = Subscription("shop", URI("https://shop.example/webhooks"), setOf("payment.created"), enabled = true)

    @Test
    fun `a failed delivery is retried later, never earlier than the schedule`() {
        deliveries.enqueue("evt_1", shop, clock.instant())
        transport.result = TransportResult(500, null, null, 3)
        deliverer.claimDue(10).forEach(deliverer::deliver)
        val queued = deliveries.queued.values.single()
        assertEquals("pending", queued.status)
        assertTrue(Duration.between(clock.instant(), queued.nextAttemptAt) >= Duration.ofSeconds(36))
    }

    @Test
    fun `retry delays follow the minimal schedule with a positive jitter`() {
        val policy = DeliveryPolicy()
        DeliveryPolicy.SCHEDULE.drop(1).forEachIndexed { index, base ->
            val delay = policy.delayAfter(index + 1, null)
            assertTrue(delay >= base.plusMillis(base.toMillis() / 5) && delay <= base.plusMillis(base.toMillis() * 2 / 5), "$delay for $base")
        }
    }

    @Test
    fun `410 stops the deliveries and the seventh failure abandons them`() {
        val policy = DeliveryPolicy()
        assertIs<DeliveryDecision.Stopped>(policy.decide(1, TransportResult(410, null, null, 1)))
        assertIs<DeliveryDecision.Abandoned>(policy.decide(7, TransportResult(500, null, null, 1)))
        assertIs<DeliveryDecision.RetryIn>(policy.decide(6, TransportResult(null, "ConnectException", null, 1)))
    }

    @Test
    fun `an endpoint is disabled after three abandoned deliveries in a row`() {
        transport.result = TransportResult(500, null, null, 1)
        repeat(3) { index ->
            deliveries.enqueue("evt_$index", shop, clock.instant())
            val id = deliveries.queued.keys.max()
            deliveries.retry(id, 6, clock.instant())
            deliverer.claimDue(10).filter { it.id == id }.forEach(deliverer::deliver)
        }
        assertTrue("shop" in health.disabled)
    }

    @Test
    fun `a 410 stops the event and disables the endpoint`() {
        deliveries.enqueue("evt_1", shop, clock.instant())
        deliveries.enqueue("evt_2", shop, clock.instant())
        transport.result = TransportResult(410, null, null, 1)
        deliverer.deliver(deliverer.claimDue(1).single())
        assertEquals(listOf("stopped", "pending"), deliveries.queued.values.map { it.status })
        assertTrue("shop" in health.disabled)
        assertTrue(signals.signals.any { (subject, message) -> subject == "shop" && message.startsWith("disabled") })
    }
}

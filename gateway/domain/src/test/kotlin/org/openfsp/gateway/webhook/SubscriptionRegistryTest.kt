package org.openfsp.gateway.webhook

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.ImmediateTransactions
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhook.spi.stubs.InMemoryDeliveries
import org.openfsp.gateway.webhook.spi.stubs.InMemoryEndpointHealth
import org.openfsp.gateway.webhook.spi.stubs.InMemorySubscriptionStore
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubscriptionRegistryTest {
    private val clock = TestClock()
    private val store = InMemorySubscriptionStore()
    private val deliveries = InMemoryDeliveries()
    private val health = InMemoryEndpointHealth()
    private val registry =
        SubscriptionRegistry(
            object : Subscriptions {
                override fun all() = store.all()
            },
            store,
            EndpointRules(plainHttpAllowed = false),
            InMemoryAuditTrail(),
            BusinessCalendar(clock),
            deliveries,
            health,
            ImmediateTransactions(),
        )

    @Test
    fun `removing a webhook stops its pending deliveries and forgets its health`() {
        val acme = registry.add("acme", URI("https://acme.example/webhooks"), setOf("payment.succeeded"))
        deliveries.enqueue("evt_1", acme, clock.instant())
        health.disable("acme", "abandoned", clock.instant())
        registry.remove("acme")
        assertEquals(0, deliveries.pending("acme"))
        assertEquals("stopped", deliveries.queued.values.single().status)
        assertFalse(health.isDisabled("acme"))
    }
}

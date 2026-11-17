package org.openfsp.gateway.webhook

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.spi.stubs.InMemoryAuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.spi.stubs.CountingTransactions
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.openfsp.gateway.webhook.spi.stubs.InMemoryDeliveries
import org.openfsp.gateway.webhook.spi.stubs.InMemoryEndpointHealth
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.webhook.spi.stubs.InMemorySubscriptionStore
import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import java.net.InetAddress
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SubscriptionRegistryTest {
    private val clock = TestClock()
    private val store = InMemorySubscriptionStore()
    private val deliveries = InMemoryDeliveries()
    private val health = InMemoryEndpointHealth()
    private val resolver =
        StaticHostResolver().apply {
            addresses["acme.example"] = listOf(InetAddress.getByName("93.184.215.14"))
            addresses["internal.example"] = listOf(InetAddress.getByName("10.0.0.5"))
        }
    private val transactions = CountingTransactions()
    private val rules = EndpointRules(plainHttpAllowed = false, privateAddressesAllowed = false)
    private val registry =
        SubscriptionRegistry(
            object : Subscriptions {
                override fun all() = store.all()
            },
            store,
            rules,
            EndpointAddresses(resolver, rules),
            InMemoryAuditTrail(),
            BusinessCalendar(clock),
            deliveries,
            health,
            transactions,
        )

    @Test
    fun `adding a webhook checks its name, saves and audits it in one transaction`() {
        registry.add("acme", URI("https://acme.example/webhooks"), setOf("payment.succeeded"))
        assertEquals(1, transactions.opened)
    }

    @Test
    fun `enabling a disabled webhook clears its health, an unknown one is not found`() {
        registry.add("acme", URI("https://acme.example/webhooks"), setOf("payment.succeeded"))
        health.disable("acme", "abandoned", clock.instant())
        assertTrue(registry.enable("acme"))
        assertFalse(health.isDisabled("acme"))
        assertFalse(registry.enable("unknown"))
    }

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

    @Test
    fun `a webhook whose host resolves to a private, loopback or metadata address is refused`() {
        listOf("https://internal.example/webhooks", "https://127.0.0.1/webhooks", "https://169.254.169.254/latest", "https://unknown.example/webhooks").forEach { url ->
            assertFailsWith<OpenFspException>(url) { registry.add("internal", URI(url), setOf("payment.succeeded")) }
        }
        assertTrue(store.all().isEmpty())
    }
}

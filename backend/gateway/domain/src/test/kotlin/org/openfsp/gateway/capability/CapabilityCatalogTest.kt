package org.openfsp.gateway.capability

import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.provider.spi.stubs.ProviderStub
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.stubs.FixedSubscriptions
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals

class CapabilityCatalogTest {
    private val providers = InMemoryProviders(listOf(ProviderStub()))

    @Test
    fun `webhooks emit is announced only while an endpoint is enabled`() {
        val endpoint = Subscription("shop", URI("https://shop.example/hooks"), setOf("payment.succeeded"), enabled = true)
        assertEquals(
            listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.WEBHOOKS_EMIT),
            CapabilityCatalog(providers, FixedSubscriptions(listOf(endpoint))).describe().single().capabilities,
        )
        assertEquals(
            listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP),
            CapabilityCatalog(providers, FixedSubscriptions(listOf(endpoint.copy(enabled = false)))).describe().single().capabilities,
        )
    }

    @Test
    fun `the supported capabilities are the provider ones and webhook emission`() {
        assertEquals(listOf("payments", "payments.lookup", "payments.statement", "webhooks.emit"), CapabilityCatalog(providers, FixedSubscriptions()).supported())
    }
}

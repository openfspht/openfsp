package org.openfsp.gateway.capability

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.account.MerchantAccount
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
        val endpoint = Subscription(MerchantAccount.DEFAULT, "shop", URI("https://shop.example/hooks"), setOf("payment.succeeded"), enabled = true)
        assertEquals(
            listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.STATEMENT, ProviderDescriptor.WEBHOOKS_EMIT),
            CapabilityCatalog(providers, FixedSubscriptions(listOf(endpoint))).describe(AccountFilter.Every).single().capabilities,
        )
        assertEquals(
            listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.STATEMENT),
            CapabilityCatalog(providers, FixedSubscriptions(listOf(endpoint.copy(enabled = false)))).describe(AccountFilter.Every).single().capabilities,
        )
    }

    @Test
    fun `payments fee is announced only for a provider that reports fees`() {
        val reporting = ProviderStub(id = "mock_beta", descriptor = ProviderStub().descriptor.let { it.copy(capabilities = it.capabilities + ProviderDescriptor.FEE) })
        val announced = CapabilityCatalog(InMemoryProviders(listOf(ProviderStub(), reporting)), FixedSubscriptions()).describe(AccountFilter.Every).associate { it.id to it.capabilities }

        assertEquals(listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.LOOKUP, ProviderDescriptor.STATEMENT), announced["mock_alpha"])
        assertEquals(listOf(ProviderDescriptor.PAYMENTS, ProviderDescriptor.FEE, ProviderDescriptor.LOOKUP, ProviderDescriptor.STATEMENT), announced["mock_beta"])
    }

    @Test
    fun `the supported capabilities are the provider ones, the statement and webhook emission`() {
        assertEquals(listOf("payments", "payments.fee", "payments.lookup", "payments.statement", "webhooks.emit"), CapabilityCatalog(providers, FixedSubscriptions()).supported())
    }
}

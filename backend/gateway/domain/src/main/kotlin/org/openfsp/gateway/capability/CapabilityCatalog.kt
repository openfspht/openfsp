package org.openfsp.gateway.capability

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.webhook.spi.Subscriptions

data class ProviderCapabilities(
    val id: String,
    val descriptor: ProviderDescriptor,
    val capabilities: List<String>,
    val limits: AmountLimits,
)

@DomainService
class CapabilityCatalog(
    private val providers: Providers,
    private val subscriptions: Subscriptions,
) : DescribeCapabilities {
    override fun describe(): List<ProviderCapabilities> {
        val emits = subscriptions.all().any { it.enabled }
        return providers.all().map { provider ->
            val announced = provider.descriptor.capabilities + listOfNotNull(ProviderDescriptor.WEBHOOKS_EMIT.takeIf { emits })
            ProviderCapabilities(provider.id, provider.descriptor, announced.sorted(), provider.limits)
        }
    }

    override fun supported(): List<String> = ProviderDescriptor.SUPPORTED
}

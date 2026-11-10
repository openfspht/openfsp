package org.openfsp.gateway.controllers.capabilities

import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.protocol.ProtocolVersion
import org.springframework.stereotype.Component

@Component
class CapabilitiesMapper {
    fun toResource(providers: List<ProviderCapabilities>): Capabilities = Capabilities(ProtocolVersion.CURRENT, providers.map(::toResource))

    fun toResource(provider: ProviderCapabilities): ProviderCapabilitiesResource =
        ProviderCapabilitiesResource(
            id = provider.id,
            displayName = provider.descriptor.displayName,
            capabilities = provider.capabilities,
            payments = toPayments(provider.descriptor, provider.limits),
        )

    private fun toPayments(
        descriptor: ProviderDescriptor,
        limits: AmountLimits,
    ): PaymentsCapability =
        PaymentsCapability(
            nextActions = descriptor.nextActions.sorted(),
            payerRequired = descriptor.payerRequired,
            returnUrlRequired = descriptor.returnUrlRequired,
            expiryGuaranteed = descriptor.expiryGuaranteed,
            limits = limits.takeIf { it.isKnown }?.let { Limits(it.minAmount, it.maxAmount, it.amountStep) },
            locales = descriptor.locales.sorted(),
        )
}

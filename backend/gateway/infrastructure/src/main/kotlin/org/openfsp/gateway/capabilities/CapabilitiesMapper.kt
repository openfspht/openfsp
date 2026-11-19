package org.openfsp.gateway.capabilities

import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.protocol.ProtocolVersion
import org.openfsp.protocol.api.CapabilitiesResource
import org.openfsp.protocol.api.LimitsResource
import org.openfsp.protocol.api.PaymentsCapabilityResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.springframework.stereotype.Component

@Component
class CapabilitiesMapper {
    fun toResource(providers: List<ProviderCapabilities>): CapabilitiesResource = CapabilitiesResource(ProtocolVersion.CURRENT, providers.map(::toResource))

    fun toResource(provider: ProviderCapabilities): ProviderCapabilitiesResource =
        ProviderCapabilitiesResource(
            id = provider.id,
            displayName = provider.descriptor.displayName,
            logoUrl = provider.descriptor.logoUrl?.toString(),
            capabilities = provider.capabilities,
            payments = toPayments(provider.descriptor, provider.limits),
        )

    private fun toPayments(
        descriptor: ProviderDescriptor,
        limits: AmountLimits,
    ): PaymentsCapabilityResource =
        PaymentsCapabilityResource(
            nextActions = descriptor.nextActions.sorted().map { it.wire },
            payerRequired = descriptor.payerRequired,
            returnUrlRequired = descriptor.returnUrlRequired,
            expiryGuaranteed = descriptor.expiryGuaranteed,
            limits = limits.takeIf { it.isKnown }?.let { LimitsResource(it.minAmount, it.maxAmount, it.amountStep) },
            locales = descriptor.locales.sorted(),
        )
}

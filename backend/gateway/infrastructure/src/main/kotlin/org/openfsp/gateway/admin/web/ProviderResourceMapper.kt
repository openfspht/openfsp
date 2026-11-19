package org.openfsp.gateway.admin.web

import org.openfsp.gateway.capability.ProviderCapabilities
import org.openfsp.gateway.shared.web.DataCollection
import org.springframework.stereotype.Component

@Component
class ProviderResourceMapper {
    fun toResource(provider: ProviderCapabilities): ProviderResource =
        ProviderResource(provider.id, provider.descriptor.displayName, provider.descriptor.logoUrl?.toString(), provider.capabilities)

    fun toCollection(providers: List<ProviderCapabilities>): DataCollection<ProviderResource> = DataCollection(providers.map(::toResource))
}

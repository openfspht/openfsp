package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

class ConfiguredProviders(
    operations: List<ProviderOperations>,
) : Providers {
    private val byId = operations.associateBy { it.id }

    override fun get(id: String): ProviderOperations? = byId[id]

    override fun all(): List<ProviderOperations> = byId.values.sortedBy { it.id }
}

package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

@Stub
class InMemoryProviders(
    operations: List<ProviderOperations> = emptyList(),
) : Providers {
    private val byId = operations.associateBy { it.id }.toMutableMap()

    fun add(operations: ProviderOperations) {
        byId[operations.id] = operations
    }

    override fun get(id: String): ProviderOperations? = byId[id]

    override fun all(): List<ProviderOperations> = byId.values.sortedBy { it.id }
}

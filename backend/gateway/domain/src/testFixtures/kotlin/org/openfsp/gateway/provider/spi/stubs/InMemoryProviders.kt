package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

@Stub
class InMemoryProviders(
    operations: List<ProviderOperations> = emptyList(),
) : Providers {
    private val platform = operations.associateBy { it.id }.toMutableMap()
    private val accounts = mutableMapOf<Pair<String, String>, ProviderOperations>()

    fun add(operations: ProviderOperations) {
        platform[operations.id] = operations
    }

    fun addForAccount(
        account: String,
        operations: ProviderOperations,
    ) {
        accounts[account to operations.id] = operations
    }

    override fun served(): List<ServedProvider> =
        (platform.values + accounts.values).distinctBy { it.id }.sortedBy { it.id }.map { ServedProvider(it.id, it.descriptor, it.limits) }

    override fun platform(id: String): ProviderOperations? = platform[id]

    override fun ofAccount(
        account: String,
        id: String,
    ): ProviderOperations? = accounts[account to id]
}

package org.openfsp.gateway.provider.spi

interface Providers {
    operator fun get(id: String): ProviderOperations?

    fun all(): List<ProviderOperations>
}

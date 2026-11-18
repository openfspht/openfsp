package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.ServedProvider

interface Providers {
    fun served(): List<ServedProvider>

    fun platform(id: String): ProviderOperations?

    fun ofAccount(
        account: String,
        id: String,
    ): ProviderOperations?
}

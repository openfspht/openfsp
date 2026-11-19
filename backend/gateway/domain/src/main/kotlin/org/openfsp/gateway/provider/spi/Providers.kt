package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.RevisionOperations
import org.openfsp.gateway.provider.ServedProvider

interface Providers {
    fun served(): List<ServedProvider>

    fun platform(id: String): ProviderOperations?

    fun active(
        account: String,
        id: String,
    ): RevisionOperations?

    fun ofRevision(
        account: String,
        id: String,
        revision: String,
    ): ProviderOperations?
}

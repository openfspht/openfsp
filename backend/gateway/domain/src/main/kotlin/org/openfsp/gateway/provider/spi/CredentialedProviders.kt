package org.openfsp.gateway.provider.spi

import org.openfsp.gateway.provider.CredentialedProvider

interface CredentialedProviders {
    fun all(): List<CredentialedProvider>

    fun find(id: String): CredentialedProvider?
}

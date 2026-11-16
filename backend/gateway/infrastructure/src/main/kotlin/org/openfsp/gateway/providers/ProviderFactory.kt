package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.ProviderOperations
import java.net.URI

data class ProviderSetup(
    val id: String,
    val settings: ProviderProperties.Provider,
    val limits: AmountLimits,
    val logoUrl: URI?,
)

interface ProviderFactory {
    val type: String

    fun create(setup: ProviderSetup): ProviderOperations
}

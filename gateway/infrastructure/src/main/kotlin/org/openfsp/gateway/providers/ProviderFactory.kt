package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.spi.ProviderOperations

interface ProviderFactory {
    val type: String

    fun create(
        id: String,
        settings: ProviderProperties.Provider,
        limits: AmountLimits,
    ): ProviderOperations
}

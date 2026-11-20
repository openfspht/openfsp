package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.AmountLimits
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.spi.ProviderOperations
import java.net.URI

data class ProviderSetup(
    val id: String,
    val settings: ProviderProperties.Provider,
    val limits: AmountLimits,
    val logoUrl: URI?,
)

fun ProviderDescriptor.configuredBy(setup: ProviderSetup): ProviderDescriptor = copy(displayName = setup.settings.displayName ?: displayName, logoUrl = setup.logoUrl)

interface ProviderFactory {
    val type: String

    val accountCredentials: List<String> get() = emptyList()

    val descriptor: ProviderDescriptor? get() = null

    fun describe(setup: ProviderSetup): ProviderDescriptor? = descriptor?.configuredBy(setup)

    fun create(setup: ProviderSetup): ProviderOperations
}

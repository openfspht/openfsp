package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.CredentialedProvider
import org.openfsp.gateway.provider.spi.CredentialedProviders
import org.springframework.stereotype.Component

@Component
class ConfiguredCredentialedProviders(
    private val providers: ConfiguredProviders,
) : CredentialedProviders {
    override fun all(): List<CredentialedProvider> = providers.all().map(::credentialed)

    override fun find(id: String): CredentialedProvider? = providers.configured(id)?.let(::credentialed)

    private fun credentialed(provider: ConfiguredProvider) =
        CredentialedProvider(
            id = provider.setup.id,
            displayName = provider.served.descriptor.displayName,
            credentialNames = provider.factory.accountCredentials,
            hasPlatformCredentials = provider.platform != null,
            lookupNotifications = provider.served.descriptor.lookupNotifications,
        )
}

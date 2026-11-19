package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.providers.persistence.ProviderCredentialStore
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

class ConfiguredProvider(
    val setup: ProviderSetup,
    val factory: ProviderFactory,
    val platform: ProviderOperations?,
) {
    val served: ServedProvider =
        ServedProvider(setup.id, platform?.descriptor ?: factory.describe(setup) ?: error("${setup.id} has no platform credentials and takes none per account"), setup.limits)

    val acceptsAccountCredentials: Boolean get() = factory.accountCredentials.isNotEmpty()
}

class ConfiguredProviders(
    configured: List<ConfiguredProvider>,
    private val credentials: ProviderCredentialStore,
) : Providers {
    private val byId = configured.associateBy { it.setup.id }
    private val ofAccounts = ConcurrentHashMap<Pair<String, String>, Built>()

    private class Built(
        val updatedAt: Instant,
        val operations: ProviderOperations,
    )

    fun configured(id: String): ConfiguredProvider? = byId[id]

    fun all(): List<ConfiguredProvider> = byId.values.sortedBy { it.setup.id }

    override fun served(): List<ServedProvider> = all().map { it.served }

    override fun platform(id: String): ProviderOperations? = byId[id]?.platform

    override fun ofAccount(
        account: String,
        id: String,
    ): ProviderOperations? {
        val provider = byId[id]?.takeIf { it.acceptsAccountCredentials } ?: return null
        val key = account to id
        val updatedAt = credentials.updatedAt(account, id) ?: return null.also { ofAccounts.remove(key) }
        ofAccounts[key]?.takeIf { it.updatedAt == updatedAt }?.let { return it.operations }
        val values = credentials.read(account, id) ?: return null
        val operations = provider.factory.create(provider.setup.copy(settings = provider.setup.settings.copy(credentials = values)))
        ofAccounts[key] = Built(updatedAt, operations)
        return operations
    }
}

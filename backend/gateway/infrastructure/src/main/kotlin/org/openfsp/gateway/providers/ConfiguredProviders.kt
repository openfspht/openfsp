package org.openfsp.gateway.providers

import org.openfsp.gateway.provider.CredentialMode
import org.openfsp.gateway.provider.RevisionOperations
import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.providers.persistence.ProviderCredentialStore
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
    private val ofRevisions = ConcurrentHashMap<String, Built>()

    private class Built(
        val account: String,
        val id: String,
        val operations: ProviderOperations,
    )

    fun configured(id: String): ConfiguredProvider? = byId[id]

    fun all(): List<ConfiguredProvider> = byId.values.sortedBy { it.setup.id }

    override fun served(): List<ServedProvider> = all().map { it.served }

    override fun platform(id: String): ProviderOperations? = byId[id]?.platform

    override fun modeOf(
        account: String,
        id: String,
    ): CredentialMode = if (byId[id]?.acceptsAccountCredentials == true) credentials.modeOf(account, id) else CredentialMode.PLATFORM

    override fun active(
        account: String,
        id: String,
    ): RevisionOperations? {
        if (byId[id]?.acceptsAccountCredentials != true) return null
        val revision = credentials.activeRevision(account, id) ?: return null
        return ofRevision(account, id, revision)?.let { RevisionOperations(revision, it) }
    }

    override fun ofRevision(
        account: String,
        id: String,
        revision: String,
    ): ProviderOperations? {
        val provider = byId[id]?.takeIf { it.acceptsAccountCredentials } ?: return null
        if (!credentials.isUsable(account, id, revision)) return null.also { ofRevisions.remove(revision) }
        ofRevisions[revision]?.let { return it.operations }
        val values = credentials.read(account, id, revision) ?: return null
        return provider.factory.create(provider.setup.copy(settings = provider.setup.settings.copy(credentials = values))).also { ofRevisions[revision] = Built(account, id, it) }
    }

    fun evictUnusable() {
        ofRevisions.entries.removeIf { (revision, built) -> !credentials.isUsable(built.account, built.id, revision) }
    }
}

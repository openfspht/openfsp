package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.provider.CredentialMode
import org.openfsp.gateway.provider.CredentialStatus
import org.openfsp.gateway.provider.RevisionOperations
import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.spi.ProviderOperations
import org.openfsp.gateway.provider.spi.Providers

@Stub
class InMemoryProviders(
    operations: List<ProviderOperations> = emptyList(),
) : Providers {
    private class Revision(
        val account: String,
        val operations: ProviderOperations,
        var status: CredentialStatus,
    )

    private val platform = operations.associateBy { it.id }.toMutableMap()
    private val revisions = linkedMapOf<String, Revision>()
    private val modes = mutableMapOf<Pair<String, String>, CredentialMode>()

    fun add(operations: ProviderOperations) {
        platform[operations.id] = operations
    }

    fun addForAccount(
        account: String,
        operations: ProviderOperations,
        revision: String = "cred_${account}_${operations.id}_${revisions.size}",
    ): String {
        revisions.values.filter { it.account == account && it.operations.id == operations.id && it.status == CredentialStatus.ACTIVE }.forEach { it.status = CredentialStatus.RETIRED }
        revisions[revision] = Revision(account, operations, CredentialStatus.ACTIVE)
        modes[account to operations.id] = CredentialMode.OWN
        return revision
    }

    fun revoke(revision: String) {
        revisions.getValue(revision).status = CredentialStatus.REVOKED
    }

    fun retire(revision: String) {
        revisions.getValue(revision).status = CredentialStatus.RETIRED
    }

    fun usePlatform(
        account: String,
        id: String,
    ) {
        revisions.values.filter { it.account == account && it.operations.id == id && it.status == CredentialStatus.ACTIVE }.forEach { it.status = CredentialStatus.RETIRED }
        modes[account to id] = CredentialMode.PLATFORM
    }

    override fun served(): List<ServedProvider> =
        (platform.values + revisions.values.map { it.operations }).distinctBy { it.id }.sortedBy { it.id }.map { ServedProvider(it.id, it.descriptor, it.limits) }

    override fun platform(id: String): ProviderOperations? = platform[id]

    override fun modeOf(
        account: String,
        id: String,
    ): CredentialMode = modes[account to id] ?: CredentialMode.PLATFORM

    override fun active(
        account: String,
        id: String,
    ): RevisionOperations? =
        revisions.entries
            .firstOrNull { (_, it) -> it.account == account && it.operations.id == id && it.status == CredentialStatus.ACTIVE }
            ?.let { (revision, it) -> RevisionOperations(revision, it.operations) }

    override fun usableRevisions(
        account: String,
        id: String,
    ): List<String> =
        revisions.entries
            .filter { (_, it) -> it.account == account && it.operations.id == id && it.status != CredentialStatus.REVOKED }
            .reversed()
            .sortedBy { (_, it) -> it.status != CredentialStatus.ACTIVE }
            .map { it.key }

    override fun ofRevision(
        account: String,
        id: String,
        revision: String,
    ): ProviderOperations? = revisions[revision]?.takeIf { it.account == account && it.operations.id == id && it.status != CredentialStatus.REVOKED }?.operations
}

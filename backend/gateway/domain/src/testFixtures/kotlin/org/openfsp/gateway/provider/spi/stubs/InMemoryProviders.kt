package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
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
        return revision
    }

    fun revoke(revision: String) {
        revisions.getValue(revision).status = CredentialStatus.REVOKED
    }

    fun retire(revision: String) {
        revisions.getValue(revision).status = CredentialStatus.RETIRED
    }

    override fun served(): List<ServedProvider> =
        (platform.values + revisions.values.map { it.operations }).distinctBy { it.id }.sortedBy { it.id }.map { ServedProvider(it.id, it.descriptor, it.limits) }

    override fun platform(id: String): ProviderOperations? = platform[id]

    override fun active(
        account: String,
        id: String,
    ): RevisionOperations? =
        revisions.entries
            .firstOrNull { (_, it) -> it.account == account && it.operations.id == id && it.status == CredentialStatus.ACTIVE }
            ?.let { (revision, it) -> RevisionOperations(revision, it.operations) }

    override fun ofRevision(
        account: String,
        id: String,
        revision: String,
    ): ProviderOperations? = revisions[revision]?.takeIf { it.account == account && it.operations.id == id && it.status != CredentialStatus.REVOKED }?.operations
}

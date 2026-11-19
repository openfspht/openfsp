package org.openfsp.gateway.provider.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.provider.CredentialMode
import org.openfsp.gateway.provider.CredentialRevision
import org.openfsp.gateway.provider.CredentialStatus
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import java.time.Instant

@Stub
class InMemoryAccountCredentialStore : AccountCredentialStore {
    val revisions = linkedMapOf<String, CredentialRevision>()
    val secrets = mutableMapOf<String, Map<String, String>>()
    val pending = mutableSetOf<String>()
    val modes = mutableMapOf<Pair<String, String>, CredentialMode>()

    fun activeOf(
        account: String,
        provider: String,
    ): CredentialRevision? = revisions.values.firstOrNull { it.account == account && it.provider == provider && it.status == CredentialStatus.ACTIVE }

    override fun rotate(
        revision: CredentialRevision,
        credentials: Map<String, String>,
    ) {
        retireActive(revision.account, revision.provider, revision.createdAt)
        revisions[revision.id] = revision
        secrets[revision.id] = credentials
    }

    override fun retireActive(
        account: String,
        provider: String,
        at: Instant,
    ): Boolean {
        val active = activeOf(account, provider) ?: return false
        revisions[active.id] = active.copy(status = CredentialStatus.RETIRED, retiredAt = at)
        return true
    }

    override fun revoke(
        account: String,
        provider: String,
        revision: String,
        at: Instant,
    ): Boolean {
        val found = revisions[revision]?.takeIf { it.account == account && it.provider == provider && it.status != CredentialStatus.REVOKED } ?: return false
        revisions[revision] = found.copy(status = CredentialStatus.REVOKED, revokedAt = at, sealed = false)
        secrets.remove(revision)
        return true
    }

    override fun revisionsOf(account: String): List<CredentialRevision> = revisions.values.filter { it.account == account }

    override fun purgeRetired(
        retiredBefore: Instant,
        batch: Int,
    ): Int {
        val purgeable =
            revisions.values
                .filter { it.status == CredentialStatus.RETIRED && it.sealed && it.retiredAt!!.isBefore(retiredBefore) && it.id !in pending }
                .take(batch)
        purgeable.forEach {
            revisions[it.id] = it.copy(sealed = false)
            secrets.remove(it.id)
        }
        return purgeable.size
    }

    override fun modesOf(account: String): Map<String, CredentialMode> = modes.filterKeys { it.first == account }.mapKeys { it.key.second }

    override fun setMode(
        account: String,
        provider: String,
        mode: CredentialMode,
        at: Instant,
    ) {
        modes[account to provider] = mode
    }
}

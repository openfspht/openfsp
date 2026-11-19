package org.openfsp.gateway.provider

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.provider.api.ManageProviderCredentials
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import org.openfsp.gateway.provider.spi.CredentialedProviders
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.ResourceIds

@DomainService
class ProviderCredentialsManager(
    private val accounts: ManageAccounts,
    private val providers: CredentialedProviders,
    private val store: AccountCredentialStore,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
    private val transactions: Transactions,
) : ManageProviderCredentials {
    override fun providersOf(account: String): List<AccountProvider> {
        accounts.find(account) ?: throw OpenFspException.notFound()
        val revisions = store.revisionsOf(account).groupBy { it.provider }
        return providers.all().map { provider ->
            val own = revisions[provider.id].orEmpty().takeIf { provider.acceptsAccountCredentials }.orEmpty().sortedByDescending { it.createdAt }
            val origin =
                when {
                    own.any { it.status == CredentialStatus.ACTIVE } -> CredentialOrigin.ACCOUNT
                    provider.hasPlatformCredentials -> CredentialOrigin.PLATFORM
                    else -> CredentialOrigin.NONE
                }
            AccountProvider(provider, origin, own)
        }
    }

    override fun save(
        account: String,
        provider: String,
        credentials: Map<String, String?>?,
    ): CredentialRevision {
        val configured = writable(account, provider)
        val given = credentials ?: throw OpenFspException.missing(CREDENTIALS)
        val required = configured.credentialNames
        val unknown = given.keys - required.toSet()
        if (unknown.isNotEmpty()) throw OpenFspException.invalid(CREDENTIALS, "unknown credentials ${unknown.sorted().joinToString()}")
        val values =
            required.associateWith { name ->
                val value = given[name]?.trim()
                if (value.isNullOrEmpty()) throw OpenFspException(ProblemType.MISSING_FIELD, errors = listOf(FieldError(JsonPointer.of(CREDENTIALS, name), FieldErrorCode.REQUIRED)))
                if (value.length > MAX_VALUE) throw OpenFspException(ProblemType.INVALID_FIELD, errors = listOf(FieldError(JsonPointer.of(CREDENTIALS, name), FieldErrorCode.TOO_LONG)))
                value
            }
        val revision = CredentialRevision(ResourceIds.generate(REVISION_PREFIX), account, provider, required.sorted(), CredentialStatus.ACTIVE, calendar.now())
        transactions.inTransaction { store.rotate(revision, values) }
        trace(AuditKind.PROVIDER_CREDENTIALS_SET, account, provider, revision.id)
        return revision
    }

    override fun remove(
        account: String,
        provider: String,
    ) {
        writable(account, provider)
        if (!transactions.inTransaction { store.retireActive(account, provider, calendar.now()) }) throw OpenFspException.notFound()
        trace(AuditKind.PROVIDER_CREDENTIALS_REMOVED, account, provider)
    }

    override fun revoke(
        account: String,
        provider: String,
        revision: String,
    ) {
        accounts.find(account) ?: throw OpenFspException.notFound()
        val found = store.revisionsOf(account).firstOrNull { it.id == revision && it.provider == provider } ?: throw OpenFspException.notFound()
        if (found.status == CredentialStatus.REVOKED) throw OpenFspException(ProblemType.STATE_CONFLICT, "The revision is already revoked")
        if (!transactions.inTransaction { store.revoke(account, provider, revision, calendar.now()) }) throw OpenFspException(ProblemType.STATE_CONFLICT, "The revision is already revoked")
        trace(AuditKind.PROVIDER_CREDENTIALS_REVOKED, account, provider, revision)
    }

    override fun purgeRetired(
        batch: Int,
        maxBatches: Int,
    ): Int {
        var purged = 0
        repeat(maxBatches) {
            val wiped = transactions.inTransaction { store.purgeRetired(calendar.now().minus(Payment.CREATION_LEASE), batch) }
            purged += wiped
            if (wiped < batch) return purged
        }
        return purged
    }

    private fun writable(
        account: String,
        provider: String,
    ): CredentialedProvider {
        val found = accounts.find(account) ?: throw OpenFspException.notFound()
        if (!found.isActiveAt(calendar.now())) throw OpenFspException(ProblemType.STATE_CONFLICT, "The merchant account has expired")
        return providers.find(provider)?.takeIf { it.acceptsAccountCredentials } ?: throw OpenFspException.notFound()
    }

    private fun trace(
        kind: AuditKind,
        account: String,
        provider: String,
        revision: String? = null,
    ) = audit.record(AuditRecord(calendar.now(), kind, null, null, kind.wire, null, account, listOfNotNull(provider, revision).joinToString(" ")))

    private companion object {
        const val CREDENTIALS = "credentials"
        const val REVISION_PREFIX = "cred"
        const val MAX_VALUE = 8192
    }
}

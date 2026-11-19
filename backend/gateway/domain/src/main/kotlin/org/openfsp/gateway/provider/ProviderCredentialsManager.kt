package org.openfsp.gateway.provider

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.provider.api.ManageProviderCredentials
import org.openfsp.gateway.provider.spi.AccountCredentialStore
import org.openfsp.gateway.provider.spi.CredentialedProviders
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.ProblemType

@DomainService
class ProviderCredentialsManager(
    private val accounts: ManageAccounts,
    private val providers: CredentialedProviders,
    private val store: AccountCredentialStore,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
) : ManageProviderCredentials {
    override fun providersOf(account: String): List<AccountProvider> {
        accounts.find(account) ?: throw OpenFspException.notFound()
        val stored = store.of(account).associateBy { it.provider }
        return providers.all().map { provider ->
            val own = stored[provider.id]?.takeIf { provider.acceptsAccountCredentials }
            val origin =
                when {
                    own != null -> CredentialOrigin.ACCOUNT
                    provider.hasPlatformCredentials -> CredentialOrigin.PLATFORM
                    else -> CredentialOrigin.NONE
                }
            AccountProvider(provider, origin, own?.updatedAt)
        }
    }

    override fun save(
        account: String,
        provider: String,
        credentials: Map<String, String?>?,
    ) {
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
        store.save(account, provider, values)
        trace(AuditKind.PROVIDER_CREDENTIALS_SET, account, provider)
    }

    override fun remove(
        account: String,
        provider: String,
    ) {
        writable(account, provider)
        if (!store.remove(account, provider)) throw OpenFspException.notFound()
        trace(AuditKind.PROVIDER_CREDENTIALS_REMOVED, account, provider)
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
    ) = audit.record(AuditRecord(calendar.now(), kind, null, null, kind.wire, null, account, provider))

    private companion object {
        const val CREDENTIALS = "credentials"
        const val MAX_VALUE = 8192
    }
}

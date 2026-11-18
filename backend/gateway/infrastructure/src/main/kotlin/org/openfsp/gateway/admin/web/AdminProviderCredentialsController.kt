package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.api.ManageAccounts
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.providers.ConfiguredProviders
import org.openfsp.gateway.providers.credentials.ProviderCredentialStore
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.JsonPointer
import org.openfsp.protocol.ProblemType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.net.URI
import java.time.Instant

data class CredentialsRequest(
    val credentials: Map<String, String?>? = null,
)

data class AccountProviderResource(
    val provider: String,
    val displayName: String,
    val source: String,
    val acceptsAccountCredentials: Boolean,
    val credentialNames: List<String>,
    val updatedAt: Instant?,
    val notificationUrl: URI?,
    val returnUrl: URI?,
)

data class AccountProviderCollection(
    val storeEnabled: Boolean,
    val data: List<AccountProviderResource>,
)

@RestController
@RequestMapping("/admin/accounts/{account}/providers")
class AdminProviderCredentialsController(
    private val accounts: ManageAccounts,
    private val providers: ConfiguredProviders,
    private val store: ProviderCredentialStore,
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
    private val gateway: GatewayProperties,
) {
    @GetMapping
    fun list(
        @PathVariable account: String,
    ): AccountProviderCollection {
        accounts.find(account) ?: throw OpenFspException.notFound()
        val stored = store.of(account).associateBy { it.provider }
        return AccountProviderCollection(
            store.enabled,
            providers.all().map { provider ->
                val id = provider.setup.id
                val own = stored[id]?.takeIf { provider.acceptsAccountCredentials }
                val source = if (own != null) ACCOUNT else if (provider.platform != null) PLATFORM else NONE
                val notifies = provider.served.descriptor.lookupNotifications && provider.acceptsAccountCredentials
                AccountProviderResource(
                    provider = id,
                    displayName = provider.served.descriptor.displayName,
                    source = source,
                    acceptsAccountCredentials = provider.acceptsAccountCredentials,
                    credentialNames = provider.factory.accountCredentials,
                    updatedAt = own?.updatedAt,
                    notificationUrl = if (notifies) addressOf("callbacks", id, account) else null,
                    returnUrl = if (notifies) addressOf("return", id, account) else null,
                )
            },
        )
    }

    @PutMapping("/{provider}/credentials")
    fun save(
        @PathVariable account: String,
        @PathVariable provider: String,
        @RequestBody request: CredentialsRequest,
    ): ResponseEntity<Void> {
        val configured = writable(account, provider)
        if (!store.enabled) throw OpenFspException(ProblemType.STATE_CONFLICT, "No credentials key is configured: set OPENFSP_CREDENTIALS_KEY to store credentials per account")
        val given = request.credentials ?: throw OpenFspException.missing(CREDENTIALS)
        val required = configured.factory.accountCredentials
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
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{provider}/credentials")
    fun remove(
        @PathVariable account: String,
        @PathVariable provider: String,
    ): ResponseEntity<Void> {
        writable(account, provider)
        if (!store.remove(account, provider)) throw OpenFspException.notFound()
        trace(AuditKind.PROVIDER_CREDENTIALS_REMOVED, account, provider)
        return ResponseEntity.noContent().build()
    }

    private fun writable(
        account: String,
        provider: String,
    ) = run {
        val found = accounts.find(account) ?: throw OpenFspException.notFound()
        if (!found.isActiveAt(calendar.now())) throw OpenFspException(ProblemType.STATE_CONFLICT, "The merchant account has expired")
        providers.configured(provider)?.takeIf { it.acceptsAccountCredentials } ?: throw OpenFspException.notFound()
    }

    private fun addressOf(
        kind: String,
        provider: String,
        account: String,
    ) = URI("${gateway.publicBaseUrl.toString().trimEnd('/')}/$kind/$provider/accounts/$account")

    private fun trace(
        kind: AuditKind,
        account: String,
        provider: String,
    ) = audit.record(AuditRecord(calendar.now(), kind, null, null, kind.wire, null, account, provider))

    private companion object {
        const val CREDENTIALS = "credentials"
        const val ACCOUNT = "account"
        const val PLATFORM = "platform"
        const val NONE = "none"
        const val MAX_VALUE = 8192
    }
}

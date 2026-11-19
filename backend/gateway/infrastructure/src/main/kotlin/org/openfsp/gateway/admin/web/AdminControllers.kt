package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.admin.readmodel.ApiKeyState
import org.openfsp.gateway.admin.readmodel.AuditFilters
import org.openfsp.gateway.admin.readmodel.AuditRecordResource
import org.openfsp.gateway.admin.readmodel.AuditSearch
import org.openfsp.gateway.admin.readmodel.KeyFilters
import org.openfsp.gateway.admin.readmodel.KeyResource
import org.openfsp.gateway.admin.readmodel.KeySearch
import org.openfsp.gateway.admin.readmodel.PageQuery
import org.openfsp.gateway.admin.readmodel.PageResource
import org.openfsp.gateway.admin.readmodel.PaymentDetailResource
import org.openfsp.gateway.admin.readmodel.PaymentDetails
import org.openfsp.gateway.admin.readmodel.PaymentFilters
import org.openfsp.gateway.admin.readmodel.PaymentSearch
import org.openfsp.gateway.admin.readmodel.SortQuery
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.api.AttestPayment
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.gateway.payments.resources.PaymentResourceMapper
import org.openfsp.gateway.shared.web.DataCollection
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.FieldErrorCode
import org.openfsp.protocol.PaymentStatus
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin")
class AdminPaymentsController(
    private val search: PaymentSearch,
    private val details: PaymentDetails,
    private val attestPayment: AttestPayment,
    private val resources: PaymentResourceMapper,
) {
    @GetMapping("/payments")
    fun search(
        @RequestParam(required = false) account: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<PaymentResource> =
        search.search(PaymentFilters(account.blankToNull(), status.blankToNull(), provider.blankToNull(), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order))

    @GetMapping("/payments/export")
    fun export(
        @RequestParam(required = false) account: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<PaymentResource> =
        search.search(PaymentFilters(account.blankToNull(), status.blankToNull(), provider.blankToNull(), q.blankToNull()), PageQuery.EXPORT, SortQuery.of(sort, order))

    @GetMapping("/payments/{id}")
    fun detail(
        @PathVariable id: String,
    ): PaymentDetailResource = details.of(id)

    @PostMapping("/payments/{id}/attestations")
    fun attest(
        @PathVariable id: String,
        @RequestBody request: AttestationRequest,
    ): PaymentDetailResource {
        val status = PaymentStatus.fromWire(request.status)?.takeIf { it.isTerminal } ?: throw OpenFspException.invalid("status", "must be a terminal state")
        val attestation =
            runCatching { Attestation(request.person, status, request.evidence, request.failureReason?.let(FailureReason::fromWire)) }
                .getOrElse { throw OpenFspException.invalid("attestation", it.message ?: "is invalid") }
        attestPayment.attest(id, attestation)
        return details.of(id)
    }
}

@RestController
@RequestMapping("/admin/keys")
class AdminKeysController(
    private val keys: ManageKeys,
    private val search: KeySearch,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) account: String?,
        @RequestParam(defaultValue = "active") status: String,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<KeyResource> {
        val wanted = ApiKeyState.entries.firstOrNull { it.name.equals(status, ignoreCase = true) } ?: throw OpenFspException.invalid("status", "must be active, expired, revoked or all")
        return search.search(KeyFilters(account.blankToNull(), wanted, q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order))
    }

    @PostMapping
    fun issue(
        @RequestBody request: KeyRequest,
    ): ResponseEntity<IssuedKeyResource> {
        if (request.account.isBlank()) throw OpenFspException.invalid("account", "is required", FieldErrorCode.REQUIRED)
        if (request.principal.isBlank()) throw OpenFspException.invalid("principal", "is required", FieldErrorCode.REQUIRED)
        val scopes = request.scopes.map { Scope.fromWire(it) ?: throw OpenFspException.invalid("scopes", "unknown scope $it") }.toSet()
        val issued = keys.issue(request.account.trim(), request.principal.trim(), scopes, request.expiresAt)
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(IssuedKeyResource(issued.keyId, issued.secret))
    }

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: String,
    ): ResponseEntity<Void> = if (keys.revoke(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}

@RestController
@RequestMapping("/admin/audit")
class AdminAuditController(
    private val search: AuditSearch,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<AuditRecordResource> = search.search(AuditFilters(kind.blankToNull(), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order))

    @GetMapping("/export")
    fun export(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<AuditRecordResource> = search.search(AuditFilters(kind.blankToNull(), q.blankToNull()), PageQuery.EXPORT, SortQuery.of(sort, order))
}

internal fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

data class ProviderResource(
    val id: String,
    val displayName: String,
    val logoUrl: String?,
    val capabilities: List<String>,
)

@RestController
@RequestMapping("/admin/providers")
class AdminProvidersController(
    private val capabilities: DescribeCapabilities,
) {
    @GetMapping
    fun providers(): DataCollection<ProviderResource> =
        DataCollection(capabilities.describe(AccountFilter.Every).map { ProviderResource(it.id, it.descriptor.displayName, it.descriptor.logoUrl?.toString(), it.capabilities) })
}

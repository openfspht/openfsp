package org.openfsp.gateway.admin.web

import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.auth.KeyFilters
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.capabilities.web.CapabilitiesMapper
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.console.api.InspectPayments
import org.openfsp.gateway.console.api.SearchAudit
import org.openfsp.gateway.console.api.SearchKeys
import org.openfsp.gateway.payment.PaymentFilters
import org.openfsp.gateway.payment.api.AttestPayment
import org.openfsp.protocol.api.PaymentResource
import org.openfsp.protocol.api.ProviderCapabilitiesResource
import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.shared.web.DataCollection
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
    private val payments: InspectPayments,
    private val attestPayment: AttestPayment,
    private val resources: PaymentResourceMapper,
    private val attestations: AttestationRequestMapper,
    private val console: ConsoleResourceMapper,
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
        console.toResource(payments.search(PaymentFilters(account.blankToNull(), status.blankToNull(), provider.blankToNull(), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order)), resources::toResource)

    @GetMapping("/payments/export")
    fun export(
        @RequestParam(required = false) account: String?,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<PaymentResource> =
        console.toResource(payments.search(PaymentFilters(account.blankToNull(), status.blankToNull(), provider.blankToNull(), q.blankToNull()), PageQuery.EXPORT, SortQuery.of(sort, order)), resources::toResource)

    @GetMapping("/payments/{id}")
    fun detail(
        @PathVariable id: String,
    ): PaymentDetailResource = console.toResource(payments.inspect(id))

    @PostMapping("/payments/{id}/attestations")
    fun attest(
        @PathVariable id: String,
        @RequestBody request: AttestationRequest,
    ): PaymentDetailResource {
        attestPayment.attest(id, attestations.toAttestation(request))
        return console.toResource(payments.inspect(id))
    }
}

@RestController
@RequestMapping("/admin/keys")
class AdminKeysController(
    private val keys: ManageKeys,
    private val search: SearchKeys,
    private val mapper: AdminKeyMapper,
    private val console: ConsoleResourceMapper,
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
    ): PageResource<KeyResource> =
        console.toResource(search.search(KeyFilters(account.blankToNull(), mapper.toState(status), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order)), console::toResource)

    @PostMapping
    fun issue(
        @RequestBody request: KeyRequest,
    ): ResponseEntity<IssuedKeyResource> {
        val issue = mapper.toIssue(request)
        val issued = keys.issue(issue.account, issue.principal, issue.scopes, issue.expiresAt)
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(mapper.toResource(issued))
    }

    @PostMapping("/{id}/revoke")
    fun revoke(
        @PathVariable id: String,
    ): ResponseEntity<Void> = if (keys.revoke(id)) ResponseEntity.noContent().build() else ResponseEntity.notFound().build()
}

@RestController
@RequestMapping("/admin/audit")
class AdminAuditController(
    private val search: SearchAudit,
    private val console: ConsoleResourceMapper,
) {
    @GetMapping
    fun search(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<AuditRecordResource> = console.toResource(search.search(AuditFilters(kind.blankToNull(), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order)), console::toResource)

    @GetMapping("/export")
    fun export(
        @RequestParam(required = false) kind: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<AuditRecordResource> = console.toResource(search.search(AuditFilters(kind.blankToNull(), q.blankToNull()), PageQuery.EXPORT, SortQuery.of(sort, order)), console::toResource)
}

internal fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

@RestController
@RequestMapping("/admin/providers")
class AdminProvidersController(
    private val capabilities: DescribeCapabilities,
    private val mapper: CapabilitiesMapper,
) {
    @GetMapping
    fun providers(): DataCollection<ProviderCapabilitiesResource> = DataCollection(capabilities.describe(AccountFilter.Every).map(mapper::toResource))
}

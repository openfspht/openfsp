package org.openfsp.gateway.admin

import org.openfsp.gateway.admin.search.AuditFilters
import org.openfsp.gateway.admin.search.AuditRecordResource
import org.openfsp.gateway.admin.search.AuditSearch
import org.openfsp.gateway.admin.search.PageQuery
import org.openfsp.gateway.admin.search.PageResource
import org.openfsp.gateway.admin.search.PaymentFilters
import org.openfsp.gateway.admin.search.PaymentSearch
import org.openfsp.gateway.admin.search.SortQuery
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.controllers.payments.Collection
import org.openfsp.gateway.controllers.payments.PaymentResource
import org.openfsp.gateway.controllers.payments.PaymentResourceMapper
import org.openfsp.gateway.controllers.statements.GatewayStatementResource
import org.openfsp.gateway.controllers.statements.StatementResourceMapper
import org.openfsp.gateway.payment.Attestation
import org.openfsp.gateway.payment.api.AttestAPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.PaymentStatus
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
    private val attestAPayment: AttestAPayment,
    private val resources: PaymentResourceMapper,
) {
    @GetMapping("/payments")
    fun search(
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) q: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "${PageQuery.DEFAULT_SIZE}") size: Int,
        @RequestParam(required = false) sort: String?,
        @RequestParam(required = false) order: String?,
    ): PageResource<PaymentResource> =
        search.search(PaymentFilters(status.blankToNull(), provider.blankToNull(), q.blankToNull()), PageQuery(page, size), SortQuery.of(sort, order))

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
        attestAPayment.attest(id, attestation)
        return details.of(id)
    }
}

@RestController
@RequestMapping("/admin")
class AdminStatementsController(
    private val statements: ReadStatements,
    private val mapper: StatementResourceMapper,
    private val calendar: BusinessCalendar,
) {
    @GetMapping("/gateway_statements")
    fun gatewayStatement(
        @RequestParam provider: String,
        @RequestParam("business_date", required = false) businessDate: String?,
    ): Collection<GatewayStatementResource> {
        val day = businessDate?.let(mapper::day) ?: calendar.today()
        return Collection(listOf(mapper.toResource(statements.gatewayStatement(provider, day))))
    }
}

@RestController
@RequestMapping("/admin/keys")
class AdminKeysController(
    private val keys: ManageKeys,
    private val mapper: KeyResourceMapper,
) {
    @GetMapping
    fun list(): Collection<KeyResource> = Collection(keys.list().map(mapper::toResource))

    @PostMapping
    fun issue(
        @RequestBody request: KeyRequest,
    ): ResponseEntity<IssuedKeyResource> {
        if (request.principal.isBlank()) throw OpenFspException.invalid("principal", "is required")
        val scopes = request.scopes.map { Scope.fromWire(it) ?: throw OpenFspException.invalid("scopes", "unknown scope $it") }.toSet()
        val issued = keys.issue(request.principal.trim(), scopes, request.expiresAt)
        return ResponseEntity.status(HttpStatus.CREATED).body(IssuedKeyResource(issued.keyId, issued.secret))
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
}

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

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
    fun providers(): Collection<ProviderResource> =
        Collection(capabilities.describe().map { ProviderResource(it.id, it.descriptor.displayName, it.descriptor.logoUrl?.toString(), it.capabilities) })
}

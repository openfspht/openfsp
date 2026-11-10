package org.openfsp.gateway.statements.web

import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payments.web.Collection
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.security.RequiresScope
import org.openfsp.gateway.statement.Match
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.protocol.ProblemType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StatementsController(
    private val statements: ReadStatements,
    private val mapper: StatementResourceMapper,
    private val capabilities: DescribeCapabilities,
) {
    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/statements")
    fun operatorStatement(
        @RequestParam provider: String,
        @RequestParam("business_date") businessDate: String,
    ): Collection<StatementResource> {
        val announced = capabilities.describe().any { it.id == provider && ProviderDescriptor.STATEMENT in it.capabilities }
        if (!announced) throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "$provider does not announce ${ProviderDescriptor.STATEMENT}")
        return Collection(listOfNotNull(statements.operatorStatement(provider, mapper.day(businessDate))?.let(mapper::toResource)))
    }

    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/statement_entries")
    fun entries(
        @RequestParam match: String,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): EntryPageResource {
        val wanted = Match.fromWire(match) ?: throw OpenFspException.invalid("match", "must be unmatched or conflict")
        val page = statements.entries(wanted, provider, limit, cursor?.let(mapper::decode))
        return EntryPageResource(page.entries.map { mapper.toResource(it.second) }, page.next?.let(mapper::encode))
    }

    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/gateway_statements")
    fun gatewayStatement(
        @RequestParam provider: String,
        @RequestParam("business_date") businessDate: String,
    ): Collection<GatewayStatementResource> = Collection(listOf(mapper.toResource(statements.gatewayStatement(provider, mapper.day(businessDate)))))
}

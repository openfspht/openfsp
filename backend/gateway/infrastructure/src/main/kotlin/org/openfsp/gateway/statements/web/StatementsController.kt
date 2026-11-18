package org.openfsp.gateway.statements.web

import org.openfsp.gateway.apikeys.RequiresScope
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.protocol.StatementPeriod
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class StatementsController(
    private val statements: ReadStatements,
    private val mapper: StatementResourceMapper,
) {
    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/statements")
    fun statement(
        @RequestParam(required = false) provider: String?,
        @RequestParam(StatementPeriod.BUSINESS_DATE, required = false) businessDate: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: String?,
        @RequestParam(required = false) cursor: String?,
        caller: AuthenticatedKey,
    ): StatementCollectionResource {
        val query = mapper.toQuery(provider, businessDate, from, to)
        return mapper.toResource(statements.page(AccountFilter.Only(caller.accountId), query.provider, query.period, mapper.toLimit(limit), mapper.toCursor(cursor)))
    }
}

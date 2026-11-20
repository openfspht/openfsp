package org.openfsp.gateway.admin.web

import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.gateway.statements.StatementResourceMapper
import org.openfsp.protocol.StatementPeriod
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/admin/statements")
class AdminStatementsController(
    private val statements: ReadStatements,
    private val mapper: StatementResourceMapper,
    private val csv: StatementCsv,
) {
    @GetMapping
    fun statement(
        @RequestParam(required = false) provider: String?,
        @RequestParam(StatementPeriod.BUSINESS_DATE, required = false) businessDate: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) limit: String?,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(required = false) account: String?,
    ): StatementCollection {
        val query = mapper.toQuery(provider, businessDate, from, to)
        return mapper.toResource(statements.page(filterOf(account), query.provider, query.period, mapper.toLimit(limit), mapper.toCursor(cursor)))
    }

    @GetMapping("/export")
    fun export(
        @RequestParam(required = false) provider: String?,
        @RequestParam(StatementPeriod.BUSINESS_DATE, required = false) businessDate: String?,
        @RequestParam(required = false) from: String?,
        @RequestParam(required = false) to: String?,
        @RequestParam(required = false) account: String?,
        response: HttpServletResponse,
    ) {
        val query = mapper.toQuery(provider, businessDate, from, to)
        val entries = statements.all(filterOf(account), query.provider, query.period)
        val filename = "statement-${query.provider}-${query.period.from}-${query.period.to}.csv"
        response.contentType = CSV
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        csv.write(entries, response.outputStream)
    }

    private fun filterOf(account: String?): AccountFilter = account.blankToNull()?.let(AccountFilter::Only) ?: AccountFilter.Every

    private companion object {
        const val CSV = "text/csv; charset=utf-8"
    }
}

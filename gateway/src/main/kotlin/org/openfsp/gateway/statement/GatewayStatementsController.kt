package org.openfsp.gateway.statement

import org.openfsp.gateway.auth.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.provider.LocalTimes
import org.openfsp.gateway.provider.Providers
import org.openfsp.gateway.web.ApiException
import org.openfsp.protocol.FailureReason
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ProblemType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

data class GatewayStatementPayment(
    val payment: String,
    val reference: String,
    val status: PaymentStatus,
    val amount: Money,
    val completedAt: Instant,
    val failureReason: FailureReason?,
)

data class StatusTotal(
    val count: Int,
    val amount: Money,
)

data class GatewayStatement(
    val provider: String,
    val businessDate: LocalDate,
    val final: Boolean,
    val payments: List<GatewayStatementPayment>,
    val totals: Map<String, StatusTotal>,
)

data class GatewayStatementCollection(
    val data: List<GatewayStatement>,
)

@RestController
class GatewayStatementsController(
    private val jdbc: JdbcClient,
    private val providers: Providers,
    private val clock: Clock,
) {
    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/gateway_statements")
    fun statement(
        @RequestParam provider: String,
        @RequestParam("business_date") businessDate: String,
    ): GatewayStatementCollection {
        if (providers[provider] == null) throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("provider", "is not served by this gateway")))
        val day =
            runCatching { LocalDate.parse(businessDate) }.getOrElse {
                throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("business_date", "must be a full-date")))
            }
        val start = LocalTimes.startOf(day)
        val end = LocalTimes.startOf(day.plusDays(1))
        val payments =
            jdbc
                .sql(
                    """
                    select id, reference, status, amount, completed_at, failure_reason from payments
                    where provider = ? and completed_at >= ? and completed_at < ?
                    order by completed_at, id
                    """.trimIndent(),
                ).params(provider, Timestamp.from(start), Timestamp.from(end))
                .query { rs, _ ->
                    GatewayStatementPayment(
                        payment = rs.getString(1),
                        reference = rs.getString(2),
                        status = PaymentStatus.fromWire(rs.getString(3))!!,
                        amount = Money.htg(rs.getLong(4)),
                        completedAt = rs.getTimestamp(5).toInstant(),
                        failureReason = rs.getString(6)?.let(FailureReason::fromWire),
                    )
                }.list()
        val totals =
            payments
                .groupBy { it.status.wire }
                .mapValues { (_, group) -> StatusTotal(group.size, Money.htg(group.sumOf { it.amount.amount })) }
                .toSortedMap()
        val final = !clock.instant().isBefore(end)
        return GatewayStatementCollection(listOf(GatewayStatement(provider, day, final, payments, totals)))
    }
}

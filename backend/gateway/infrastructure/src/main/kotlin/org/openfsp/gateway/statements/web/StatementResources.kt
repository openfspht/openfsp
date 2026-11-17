package org.openfsp.gateway.statements.web

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.statement.EntryCursor
import org.openfsp.gateway.statement.GatewayStatement
import org.openfsp.gateway.statement.OperatorStatement
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

data class StatementEntryResource(
    val type: String,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val bookedAt: Instant,
    val payment: String?,
    val match: String,
)

data class StatementResource(
    val id: String,
    val provider: String,
    val businessDate: LocalDate,
    val complete: Boolean,
    val entries: List<StatementEntryResource>,
    val missingPayments: List<String>,
)

data class EntryPageResource(
    val data: List<StatementEntryResource>,
    val nextCursor: String?,
)

data class GatewayStatementPaymentResource(
    val payment: String,
    val reference: String,
    val status: PaymentStatus,
    val amount: Money,
    val completedAt: Instant,
)

data class StatusTotalResource(
    val count: Int,
    val amount: Money,
)

data class GatewayStatementResource(
    val provider: String,
    val businessDate: LocalDate,
    val final: Boolean,
    val payments: List<GatewayStatementPaymentResource>,
    val totals: Map<String, StatusTotalResource>,
)

@Component
class StatementResourceMapper {
    fun toResource(entry: StatementEntry) =
        StatementEntryResource(
            entry.type,
            entry.amount,
            entry.feeAmount,
            entry.netAmount,
            entry.bookedAt,
            entry.paymentId,
            entry.match.wire,
        )

    fun toResource(statement: OperatorStatement) =
        StatementResource(
            statement.id,
            statement.provider,
            statement.businessDate,
            statement.complete,
            statement.entries.map(::toResource),
            statement.missingPayments,
        )

    fun toResource(statement: GatewayStatement) =
        GatewayStatementResource(
            provider = statement.provider,
            businessDate = statement.businessDate,
            final = statement.final,
            payments =
                statement.payments.map {
                    GatewayStatementPaymentResource(it.id, it.reference, it.status, it.amount, it.completedAt!!)
                },
            totals = statement.totals.entries.associate { (status, total) -> status.wire to StatusTotalResource(total.count, total.amount) }.toSortedMap(),
        )

    fun encode(cursor: EntryCursor): String = Base64.getUrlEncoder().withoutPadding().encodeToString("${cursor.bookedAt}|${cursor.position}".toByteArray())

    fun decode(cursor: String): EntryCursor =
        runCatching {
            val (at, position) = String(Base64.getUrlDecoder().decode(cursor)).split('|', limit = 2)
            EntryCursor(Instant.parse(at), position.toLong())
        }.getOrElse { throw OpenFspException.invalid("cursor", "is not a cursor from this gateway") }

    fun day(value: String): LocalDate = runCatching { LocalDate.parse(value) }.getOrElse { throw OpenFspException.invalid("business_date", "must be a full-date") }
}

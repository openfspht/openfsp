package org.openfsp.gateway.statement

import org.openfsp.gateway.auth.RequiresScope
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.provider.LocalTimes
import org.openfsp.gateway.web.ApiException
import org.openfsp.protocol.FieldError
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.util.Base64

data class StatementEntry(
    val providerReference: String,
    val type: String,
    val amount: Money,
    val feeAmount: Money?,
    val netAmount: Money?,
    val bookedAt: Instant,
    val providerOrderId: String?,
    val payment: String?,
    val match: String,
    val source: String,
)

data class StatementResource(
    val id: String,
    val provider: String,
    val businessDate: LocalDate,
    val complete: Boolean,
    val entries: List<StatementEntry>,
    val missingPayments: List<String>,
)

data class StatementCollection(
    val data: List<StatementResource>,
)

data class EntryPage(
    val data: List<StatementEntry>,
    val nextCursor: String?,
)

@RestController
class StatementsController(
    private val jdbc: JdbcClient,
) {
    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/statements")
    fun statement(
        @RequestParam provider: String,
        @RequestParam("business_date") businessDate: String,
    ): StatementCollection {
        val day =
            runCatching { LocalDate.parse(businessDate) }.getOrElse {
                throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("business_date", "must be a full-date")))
            }
        val found =
            jdbc
                .sql("select id, complete from statements where provider = ? and business_date = ?")
                .params(provider, day)
                .query { rs, _ -> rs.getString(1) to rs.getBoolean(2) }
                .optional()
                .orElse(null) ?: return StatementCollection(emptyList())
        val (id, complete) = found
        val entries =
            jdbc
                .sql("select $COLUMNS from statement_entries where statement_id = ? order by booked_at, id")
                .param(id)
                .query { rs, _ -> entry(rs) }
                .list()
        val missing =
            if (!complete) {
                emptyList()
            } else {
                jdbc
                    .sql(
                        """
                        select p.id from payments p
                        where p.provider = ? and p.status = 'succeeded' and p.completed_at >= ? and p.completed_at < ?
                          and not exists (select 1 from statement_entries e where e.statement_id = ? and e.payment_id = p.id)
                        order by p.completed_at
                        """.trimIndent(),
                    ).params(provider, Timestamp.from(LocalTimes.startOf(day)), Timestamp.from(LocalTimes.startOf(day.plusDays(1))), id)
                    .query(String::class.java)
                    .list()
                    .filterNotNull()
            }
        return StatementCollection(listOf(StatementResource(id, provider, day, complete, entries, missing)))
    }

    @RequiresScope(Scope.STATEMENTS_READ)
    @GetMapping("/v1/statement_entries")
    fun entries(
        @RequestParam match: String,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false, defaultValue = "50") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): EntryPage {
        if (match !in setOf(Statements.UNMATCHED, Statements.CONFLICT)) {
            throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("match", "must be unmatched or conflict")))
        }
        if (limit !in 1..MAX_LIMIT) throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("limit", "must be between 1 and 100")))
        val after = cursor?.let(::decode)
        val rows =
            jdbc
                .sql(
                    """
                    select e.id, $PREFIXED from statement_entries e join statements s on s.id = e.statement_id
                    where e.match = :match and (cast(:provider as text) is null or s.provider = :provider)
                      and (cast(:afterAt as timestamptz) is null or (e.booked_at, e.id) > (cast(:afterAt as timestamptz), :afterId))
                    order by e.booked_at, e.id
                    limit :limit
                    """.trimIndent(),
                ).param("match", match)
                .param("provider", provider)
                .param("afterAt", after?.first?.let(Timestamp::from))
                .param("afterId", after?.second ?: 0L)
                .param("limit", limit + 1)
                .query { rs, _ -> rs.getLong(1) to entry(rs, offset = 1) }
                .list()
        val page = rows.take(limit)
        val next = if (rows.size > limit) page.last().let { (id, entry) -> encode(entry.bookedAt, id) } else null
        return EntryPage(page.map { it.second }, next)
    }

    private fun entry(
        rs: ResultSet,
        offset: Int = 0,
    ) = StatementEntry(
        providerReference = rs.getString(offset + 1),
        type = rs.getString(offset + 2),
        amount = Money.htg(rs.getLong(offset + 3)),
        feeAmount = rs.getObject(offset + 4)?.let { Money.htg((it as Number).toLong()) },
        netAmount = rs.getObject(offset + 5)?.let { Money.htg((it as Number).toLong()) },
        bookedAt = rs.getTimestamp(offset + 6).toInstant(),
        providerOrderId = rs.getString(offset + 7),
        payment = rs.getString(offset + 8),
        match = rs.getString(offset + 9),
        source = rs.getString(offset + 10),
    )

    private fun encode(
        at: Instant,
        id: Long,
    ): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id".toByteArray())

    private fun decode(cursor: String): Pair<Instant, Long> =
        runCatching {
            val (at, id) = String(Base64.getUrlDecoder().decode(cursor)).split('|', limit = 2)
            Instant.parse(at) to id.toLong()
        }.getOrElse { throw ApiException(ProblemType.INVALID_FIELD, errors = listOf(FieldError("cursor", "is not a cursor from this gateway"))) }

    private companion object {
        const val MAX_LIMIT = 100
        const val COLUMNS = "provider_reference, type, amount, fee_amount, net_amount, booked_at, provider_order_id, payment_id, match, source"
        const val PREFIXED =
            "e.provider_reference, e.type, e.amount, e.fee_amount, e.net_amount, e.booked_at, e.provider_order_id, e.payment_id, e.match, e.source"
    }
}

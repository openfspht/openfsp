package org.openfsp.gateway.statements

import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.statement.StatementCursor
import org.openfsp.gateway.statement.StatementEntry
import org.openfsp.gateway.statement.StatementPage
import org.openfsp.protocol.StatementPeriod
import org.openfsp.protocol.api.StatementCollection
import org.openfsp.protocol.api.StatementEntryResource
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.Base64

data class StatementQuery(
    val provider: String,
    val period: StatementPeriod,
)

@Component
class StatementResourceMapper(
    private val payments: PaymentResourceMapper,
) {
    fun toResource(page: StatementPage) = StatementCollection(page.entries.map(::toResource), page.next?.let(::encode))

    fun toResource(entry: StatementEntry) =
        StatementEntryResource(
            provider = entry.provider,
            businessDate = entry.businessDate,
            bookedAt = entry.bookedAt,
            amount = entry.amount,
            fee = entry.fee?.let(payments::toResource),
            payment = entry.paymentId,
            reference = entry.reference,
        )

    fun toQuery(
        provider: String?,
        businessDate: String?,
        from: String?,
        to: String?,
    ): StatementQuery {
        val wanted = provider?.trim()?.takeIf { it.isNotEmpty() } ?: throw OpenFspException.missing(PROVIDER)
        return when (val requested = StatementPeriod.of(businessDate, from, to)) {
            is StatementPeriod.Request.Accepted -> StatementQuery(wanted, requested.period)
            is StatementPeriod.Request.Missing -> throw OpenFspException.missing(StatementPeriod.BUSINESS_DATE, "a period is required: business_date, or from and to")
            is StatementPeriod.Request.Invalid -> throw OpenFspException.invalid(requested.parameter, requested.detail)
        }
    }

    fun toLimit(limit: String?): Int =
        when {
            limit.isNullOrBlank() -> StatementPeriod.DEFAULT_LIMIT
            else -> limit.toIntOrNull() ?: throw OpenFspException.invalid("limit", "must be an integer")
        }

    fun toCursor(cursor: String?): StatementCursor? =
        cursor?.takeIf { it.isNotBlank() }?.let {
            runCatching {
                val (at, paymentId) = String(Base64.getUrlDecoder().decode(it)).split(SEPARATOR, limit = 2)
                StatementCursor(Instant.parse(at), paymentId)
            }.getOrElse { throw OpenFspException.invalid("cursor", "is not a cursor from this gateway") }
        }

    private fun encode(cursor: StatementCursor): String = Base64.getUrlEncoder().withoutPadding().encodeToString("${cursor.bookedAt}$SEPARATOR${cursor.paymentId}".toByteArray())

    private companion object {
        const val PROVIDER = "provider"
        const val SEPARATOR = '|'
    }
}

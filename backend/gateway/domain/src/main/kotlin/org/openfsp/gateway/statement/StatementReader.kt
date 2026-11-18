package org.openfsp.gateway.statement

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.protocol.StatementPeriod

@DomainService
class StatementReader(
    private val providers: Providers,
    private val payments: Payments,
    private val calendar: BusinessCalendar,
) : ReadStatements {
    override fun page(
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        if (limit !in StatementPeriod.MIN_LIMIT..StatementPeriod.MAX_LIMIT) {
            throw OpenFspException.invalid("limit", "must be between ${StatementPeriod.MIN_LIMIT} and ${StatementPeriod.MAX_LIMIT}")
        }
        return read(provider, period, limit, after)
    }

    override fun all(
        provider: String,
        period: StatementPeriod,
    ): Sequence<StatementEntry> = generateSequence(read(provider, period, BATCH, null)) { page -> page.next?.let { read(provider, period, BATCH, it) } }.flatMap { it.entries }

    private fun read(
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        providers[provider] ?: throw OpenFspException.invalid("provider", "is not served by this gateway")
        val rows = payments.succeededBetween(provider, calendar.startOf(period.from), calendar.endOf(period.to), after, limit + 1)
        val entries = rows.take(limit).map(::entryOf)
        return StatementPage(entries, entries.lastOrNull()?.takeIf { rows.size > limit }?.let { StatementCursor(it.bookedAt, it.paymentId) })
    }

    private fun entryOf(payment: Payment): StatementEntry {
        val bookedAt = payment.completedAt!!
        return StatementEntry(
            provider = payment.provider,
            businessDate = calendar.businessDate(bookedAt),
            bookedAt = bookedAt,
            amount = payment.amount,
            fee = payment.fee,
            paymentId = payment.id,
            reference = payment.reference,
        )
    }

    private companion object {
        const val BATCH = 500
    }
}

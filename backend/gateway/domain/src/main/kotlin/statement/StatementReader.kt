package org.openfsp.gateway.statement

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.gateway.provider.ProviderDescriptor
import org.openfsp.gateway.provider.ServedProvider
import org.openfsp.gateway.provider.servedOrNull
import org.openfsp.protocol.ProblemType
import org.openfsp.protocol.StatementPeriod

@DomainService
class StatementReader(
    private val providers: Providers,
    private val payments: Payments,
    private val calendar: BusinessCalendar,
) : ReadStatements {
    override fun statement(
        account: String,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        if (!served(provider).descriptor.hasStatement) throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "The provider $provider does not declare ${ProviderDescriptor.STATEMENT}")
        checkLimit(limit)
        return read(AccountFilter.Only(account), provider, period, limit, after)
    }

    override fun page(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        checkLimit(limit)
        served(provider)
        return read(accounts, provider, period, limit, after)
    }

    override fun all(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
    ): Sequence<StatementEntry> {
        served(provider)
        return generateSequence(read(accounts, provider, period, BATCH, null)) { page -> page.next?.let { read(accounts, provider, period, BATCH, it) } }.flatMap { it.entries }
    }

    private fun served(provider: String): ServedProvider = providers.servedOrNull(provider) ?: throw OpenFspException.invalid("provider", "is not served by this gateway")

    private fun checkLimit(limit: Int) {
        if (limit !in StatementPeriod.MIN_LIMIT..StatementPeriod.MAX_LIMIT) {
            throw OpenFspException.invalid("limit", "must be between ${StatementPeriod.MIN_LIMIT} and ${StatementPeriod.MAX_LIMIT}")
        }
    }

    private fun read(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        val rows = payments.succeededBetween(accounts, provider, calendar.startOf(period.from), calendar.endOf(period.to), after, limit + 1)
        val entries = rows.take(limit).map(::entryOf)
        return StatementPage(entries, entries.lastOrNull()?.takeIf { rows.size > limit }?.let { StatementCursor(it.bookedAt, it.paymentId) })
    }

    private fun entryOf(payment: Payment): StatementEntry {
        val bookedAt = payment.bookedAt ?: payment.completedAt!!
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

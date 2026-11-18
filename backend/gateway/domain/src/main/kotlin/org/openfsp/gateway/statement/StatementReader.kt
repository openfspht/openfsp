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
        val served = providers.served().firstOrNull { it.id == provider } ?: throw OpenFspException.invalid("provider", "is not served by this gateway")
        if (!served.descriptor.hasStatement) throw OpenFspException(ProblemType.CAPABILITY_NOT_SUPPORTED, "The provider $provider does not declare ${ProviderDescriptor.STATEMENT}")
        return page(AccountFilter.Only(account), provider, period, limit, after)
    }

    override fun page(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        if (limit !in StatementPeriod.MIN_LIMIT..StatementPeriod.MAX_LIMIT) {
            throw OpenFspException.invalid("limit", "must be between ${StatementPeriod.MIN_LIMIT} and ${StatementPeriod.MAX_LIMIT}")
        }
        return read(accounts, provider, period, limit, after)
    }

    override fun all(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
    ): Sequence<StatementEntry> = generateSequence(read(accounts, provider, period, BATCH, null)) { page -> page.next?.let { read(accounts, provider, period, BATCH, it) } }.flatMap { it.entries }

    private fun read(
        accounts: AccountFilter,
        provider: String,
        period: StatementPeriod,
        limit: Int,
        after: StatementCursor?,
    ): StatementPage {
        if (providers.served().none { it.id == provider }) throw OpenFspException.invalid("provider", "is not served by this gateway")
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

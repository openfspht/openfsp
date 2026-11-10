package org.openfsp.gateway.statement

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.spi.OperatorSignals
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.TransitionSource
import org.openfsp.gateway.payment.api.SettleAPayment
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.provider.ProviderFailure
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.provider.spi.Providers
import org.openfsp.gateway.statement.api.ReadStatements
import org.openfsp.gateway.statement.api.ReconcileStatements
import org.openfsp.gateway.statement.spi.StatementStore
import org.openfsp.protocol.PaymentStatus
import java.time.LocalDate

@DomainService
class StatementReconciler(
    private val providers: Providers,
    private val payments: Payments,
    private val settlement: SettleAPayment,
    private val statements: StatementStore,
    private val signals: OperatorSignals,
    private val calendar: BusinessCalendar,
) : ReconcileStatements,
    ReadStatements {
    override fun fetchRecent() {
        val today = calendar.today()
        providers.all().filter { it.descriptor.hasStatement }.forEach { provider ->
            listOf(today.minusDays(1), today).forEach { day ->
                runCatching { fetch(provider.id, day) }.onFailure { signals.signal(provider.id, "statement of $day not fetched: ${it.message}") }
            }
        }
    }

    override fun fetch(
        provider: String,
        day: LocalDate,
    ) {
        val operations = providers[provider]?.takeIf { it.descriptor.hasStatement } ?: return
        if (statements.isComplete(provider, day)) return
        val statement =
            try {
                operations.statement(day)
            } catch (failure: ProviderFailure) {
                throw failure.toOpenFsp()
            }
        val statementId = statements.open(provider, day, calendar.now())
        statement.lines.filterNot { statements.hasEntry(statementId, it.providerReference) }.forEach { statements.add(statementId, reconcile(provider, it)) }
        if (statement.closed) close(statementId, provider, day)
    }

    override fun operatorStatement(
        provider: String,
        day: LocalDate,
    ): OperatorStatement? {
        val (id, complete) = statements.find(provider, day) ?: return null
        val entries = statements.entries(id)
        val missing =
            if (!complete) {
                emptyList()
            } else {
                val mentioned = entries.mapNotNull { it.paymentId }.toSet()
                payments
                    .completedBetween(provider, calendar.startOf(day), calendar.endOf(day))
                    .filter { it.status == PaymentStatus.SUCCEEDED && it.id !in mentioned }
                    .map { it.id }
            }
        return OperatorStatement(id, provider, day, complete, entries, missing)
    }

    override fun entries(
        match: Match,
        provider: String?,
        limit: Int,
        after: EntryCursor?,
    ): EntryPage {
        if (match == Match.MATCHED) throw OpenFspException.invalid("match", "must be unmatched or conflict")
        if (limit !in 1..MAX_PAGE) throw OpenFspException.invalid("limit", "must be between 1 and $MAX_PAGE")
        val rows = statements.page(match, provider, after, limit + 1)
        val page = rows.take(limit)
        val next = if (rows.size > limit) page.last().let { (position, entry) -> EntryCursor(entry.bookedAt, position) } else null
        return EntryPage(page, next)
    }

    override fun gatewayStatement(
        provider: String,
        day: LocalDate,
    ): GatewayStatement {
        if (providers[provider] == null) throw OpenFspException.invalid("provider", "is not served by this gateway")
        val end = calendar.endOf(day)
        return GatewayStatement(provider, day, !calendar.now().isBefore(end), payments.completedBetween(provider, calendar.startOf(day), end))
    }

    private fun reconcile(
        provider: String,
        line: ProviderStatementLine,
    ): StatementEntry {
        val payment = payments.withProviderReference(provider, line.providerReference) ?: line.orderId?.let { payments.withOrder(provider, it) }
        val match = payment?.let { matchOf(it, line) } ?: Match.UNMATCHED
        if (match == Match.UNMATCHED) signals.signal(provider, "unmatched statement line ${line.providerReference} of ${line.amount.amount} cents")
        return StatementEntry(line.providerReference, line.type, line.amount, line.feeAmount, line.netAmount, line.bookedAt, line.orderId, payment?.id, match)
    }

    private fun matchOf(
        payment: Payment,
        line: ProviderStatementLine,
    ): Match {
        if (line.type != PAYMENT) return Match.MATCHED
        val settled =
            settlement.apply(
                payment.id,
                ProviderReport(payment.providerOrderId, line.providerReference, PaymentStatus.SUCCEEDED, line.amount),
                TransitionSource.STATEMENT,
            )
        return if (settled.status == PaymentStatus.SUCCEEDED && settled.amount == line.amount) Match.MATCHED else Match.CONFLICT
    }

    private fun close(
        statementId: String,
        provider: String,
        day: LocalDate,
    ) {
        val mentioned = statements.entries(statementId).mapNotNull { it.paymentId }.toSet()
        val until = minOf(calendar.now(), calendar.endOf(day))
        payments.pendingExpiringBetween(provider, calendar.startOf(day), until).filter { it.id !in mentioned }.forEach { payment ->
            settlement.apply(
                payment.id,
                ProviderReport(payment.providerOrderId, payment.providerReference, PaymentStatus.EXPIRED, payment.amount),
                TransitionSource.STATEMENT,
            )
        }
        statements.complete(statementId)
    }

    private companion object {
        const val PAYMENT = "payment"
        const val MAX_PAGE = 100
    }
}

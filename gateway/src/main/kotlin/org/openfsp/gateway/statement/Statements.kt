package org.openfsp.gateway.statement

import org.openfsp.gateway.payment.PaymentEntity
import org.openfsp.gateway.payment.PaymentRepository
import org.openfsp.gateway.payment.Payments
import org.openfsp.gateway.provider.LocalTimes
import org.openfsp.gateway.provider.ProviderException
import org.openfsp.gateway.provider.ProviderReport
import org.openfsp.gateway.provider.ProviderStatementLine
import org.openfsp.gateway.provider.Providers
import org.openfsp.gateway.provider.Source
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.ResourceIds
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock
import java.time.LocalDate

@Service
class Statements(
    private val providers: Providers,
    private val payments: Payments,
    private val repository: PaymentRepository,
    private val jdbc: JdbcClient,
    private val transactions: TransactionTemplate,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun fetchRecent() {
        val today = LocalTimes.businessDate(clock.instant())
        providers.ids.filter { providers[it]!!.descriptor.hasStatement }.forEach { provider ->
            listOf(today.minusDays(1), today).forEach { day ->
                runCatching { fetch(provider, day) }.onFailure { log.info("Statement {} {} not fetched: {}", provider, day, it.message) }
            }
        }
    }

    fun fetch(
        provider: String,
        day: LocalDate,
    ) {
        val adapter = providers[provider]?.takeIf { it.descriptor.hasStatement } ?: return
        if (isComplete(provider, day)) return
        val statement =
            try {
                adapter.statement(day)
            } catch (exception: ProviderException) {
                throw IllegalStateException(exception.message, exception)
            }
        val statementId = statementId(provider, day)
        statement.lines.forEach { line -> record(statementId, provider, line) }
        if (statement.closed) close(statementId, provider, day)
    }

    private fun record(
        statementId: String,
        provider: String,
        line: ProviderStatementLine,
    ) {
        val known =
            jdbc
                .sql("select count(*) from statement_entries where statement_id = ? and provider_reference = ?")
                .params(statementId, line.providerReference)
                .query(Long::class.java)
                .single() > 0
        if (known) return
        val payment =
            repository.findByProviderAndProviderReference(provider, line.providerReference)
                ?: line.orderId?.let { repository.findByProviderAndProviderOrderId(provider, it) }
        val match = payment?.let { reconcile(it, line) } ?: UNMATCHED
        if (match == UNMATCHED) log.warn("Unmatched {} statement line {} of {} cents", provider, line.providerReference, line.amount.amount)
        jdbc
            .sql(
                """
                insert into statement_entries
                    (statement_id, provider_reference, type, amount, fee_amount, net_amount, booked_at, provider_order_id, payment_id, match, source)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'provider')
                """.trimIndent(),
            ).params(
                listOf(
                    statementId,
                    line.providerReference,
                    line.type,
                    line.amount.amount,
                    line.feeAmount?.amount,
                    line.netAmount?.amount,
                    Timestamp.from(line.bookedAt),
                    line.orderId,
                    payment?.id,
                    match,
                ),
            ).update()
    }

    private fun reconcile(
        payment: PaymentEntity,
        line: ProviderStatementLine,
    ): String {
        if (line.type != PAYMENT) return MATCHED
        val settled =
            payments.apply(
                payment.id,
                ProviderReport(payment.providerOrderId, line.providerReference, PaymentStatus.SUCCEEDED, line.amount),
                Source.STATEMENT,
            )
        return if (settled.status == PaymentStatus.SUCCEEDED.wire && settled.amount == line.amount.amount) MATCHED else CONFLICT
    }

    private fun close(
        statementId: String,
        provider: String,
        day: LocalDate,
    ) {
        val endOfDay = LocalTimes.startOf(day.plusDays(1))
        val mentioned =
            jdbc
                .sql("select payment_id from statement_entries where statement_id = ? and payment_id is not null")
                .param(statementId)
                .query(String::class.java)
                .list()
                .toSet()
        repository
            .findPendingExpiredBefore(provider, PaymentStatus.PENDING.wire, endOfDay)
            .filter { it.id !in mentioned }
            .forEach { payment ->
                payments.apply(
                    payment.id,
                    ProviderReport(payment.providerOrderId, payment.providerReference, PaymentStatus.EXPIRED, Money.htg(payment.amount)),
                    Source.STATEMENT,
                )
            }
        jdbc.sql("update statements set complete = true where id = ?").param(statementId).update()
        log.info("Statement {} {} closed", provider, day)
    }

    private fun isComplete(
        provider: String,
        day: LocalDate,
    ): Boolean =
        jdbc
            .sql("select complete from statements where provider = ? and business_date = ?")
            .params(provider, day)
            .query(Boolean::class.java)
            .optional()
            .orElse(false)

    private fun statementId(
        provider: String,
        day: LocalDate,
    ): String =
        transactions.execute {
            jdbc
                .sql("insert into statements (id, provider, business_date, fetched_at) values (?, ?, ?, ?) on conflict (provider, business_date) do update set fetched_at = excluded.fetched_at")
                .params(ResourceIds.generate("stm", clock), provider, day, Timestamp.from(clock.instant()))
                .update()
            jdbc
                .sql("select id from statements where provider = ? and business_date = ?")
                .params(provider, day)
                .query(String::class.java)
                .single()
        }

    companion object {
        const val PAYMENT = "payment"
        const val MATCHED = "matched"
        const val UNMATCHED = "unmatched"
        const val CONFLICT = "conflict"
    }
}

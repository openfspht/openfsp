package org.openfsp.gateway.console

import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.audit.spi.AuditQueries
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.common.spi.stubs.CountingTransactions
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentFilters
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payment.StatusTotal
import org.openfsp.gateway.payment.spi.PaymentQueries
import org.openfsp.gateway.webhook.AttemptActivity
import org.openfsp.gateway.webhook.EndpointCondition
import org.openfsp.gateway.webhook.RecordedAttempt
import org.openfsp.gateway.webhook.RecordedEvent
import org.openfsp.gateway.webhook.spi.DeliveryQueries
import org.openfsp.protocol.Money
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ConsoleActivityTest {
    private val start = Instant.parse("2026-11-17T10:00:00Z")
    private val summary = PaymentSummary("pay_1", "INV-1", "mock_alpha", Money(100), "pending")

    private val payments =
        object : PaymentQueries {
            override fun search(
                filters: PaymentFilters,
                page: PageQuery,
                sort: SortQuery,
            ): Page<Payment> = error("not read")

            override fun totalsSince(since: Instant): List<StatusTotal> = error("not read")

            override fun transitionsOf(paymentId: String): List<RecordedTransition> = error("not read")

            override fun conflictsOf(paymentId: String): List<RecordedConflict> = error("not read")

            override fun indeterminateOf(paymentId: String): List<String> = error("not read")

            override fun latestTransitions(
                sources: Set<String>,
                limit: Int,
            ) = listOf(RecordedTransition(7, "pay_1", "pending", "succeeded", "callback", start.plusSeconds(30)), RecordedTransition(6, "pay_1", null, "pending", "creation", start)).take(limit)

            override fun countTransitionsUpTo(
                sources: Set<String>,
                cap: Int,
            ) = 2L

            override fun latestConflicts(limit: Int) = listOf(RecordedConflict(3, "pay_gone", "lookup", "{}", start.plusSeconds(20))).take(limit)

            override fun countConflictsUpTo(cap: Int) = 1L

            override fun summaries(ids: Set<String>) = mapOf("pay_1" to summary).filterKeys { it in ids }
        }

    private val deliveries =
        object : DeliveryQueries {
            override fun retryingCount() = 0L

            override fun disabledEndpointCount() = 0L

            override fun conditionOf(endpoint: String): EndpointCondition? = null

            override fun eventsOf(paymentId: String): List<RecordedEvent> = emptyList()

            override fun latestAttempts(limit: Int) = listOf(AttemptActivity(RecordedAttempt(9, 1, start.plusSeconds(40), 200, null, 12), "shop", "payment.succeeded", "pay_1")).take(limit)

            override fun countAttemptsUpTo(cap: Int) = 1L
        }

    private val audit =
        object : AuditQueries {
            override fun search(
                filters: AuditFilters,
                page: PageQuery,
                sort: SortQuery,
            ): Page<AuditEntry> = error("not read")

            override fun latest(
                kinds: Set<String>,
                limit: Int,
            ) = listOf(AuditEntry(4, start.plusSeconds(10), "key_issued", null, "ofsp_test_abc", "key_issued", null, "key_issued", null, null)).filter { it.kind in kinds }

            override fun countUpTo(
                kinds: Set<String>,
                cap: Int,
            ) = if ("key_issued" in kinds) 1L else 0L
        }

    private val transactions = CountingTransactions()
    private val activity = ConsoleActivity(payments, deliveries, audit, transactions)

    @Test
    fun `every kind is merged newest first, with the payment it concerns when it still exists`() {
        val page = activity.page(ActivityKind.entries.toSet(), emptySet(), PageQuery(0, 10))

        assertEquals(listOf("delivery-9", "transition-7", "conflict-3", "audit-4", "transition-6"), page.items.map { it.id })
        assertEquals(5L, page.totalElements)
        assertEquals(summary, page.items.first().payment)
        assertEquals(null, page.items.single { it.kind == ActivityKind.CONFLICT }.payment)
        assertEquals(ActivityKind.KEY, page.items.single { it.id == "audit-4" }.kind)
        assertEquals(1, transactions.opened)
    }

    @Test
    fun `a page reads only the kinds asked for and as deep as the page reaches`() {
        val page = activity.page(setOf(ActivityKind.TRANSITION, ActivityKind.ADMIN), emptySet(), PageQuery(0, 1))

        assertEquals(listOf("transition-7"), page.items.map { it.id })
        assertEquals(2L, page.totalElements)
        assertEquals(2, page.totalPages)
    }
}

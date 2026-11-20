package org.openfsp.gateway.console

import org.openfsp.gateway.TestClock
import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.audit.spi.AuditQueries
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.auth.KeyFilters
import org.openfsp.gateway.auth.KeyView
import org.openfsp.gateway.auth.spi.KeyQueries
import org.openfsp.gateway.capability.CapabilityCatalog
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
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
import org.openfsp.gateway.payment.spi.stubs.InMemoryPayments
import org.openfsp.gateway.provider.spi.stubs.InMemoryProviders
import org.openfsp.gateway.webhook.AttemptActivity
import org.openfsp.gateway.webhook.EndpointCondition
import org.openfsp.gateway.webhook.RecordedEvent
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.DeliveryQueries
import org.openfsp.gateway.webhook.spi.stubs.FixedSubscriptions
import org.openfsp.gateway.webhook.spi.stubs.InMemoryDeliveries
import org.openfsp.protocol.Money
import org.openfsp.protocol.ProblemType
import java.net.URI
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConsoleQueriesTest {
    private val clock = TestClock()
    private val calendar = BusinessCalendar(clock)
    private val disabledAt = Instant.parse("2026-11-09T14:00:00Z")
    private val subscriptions =
        FixedSubscriptions(
            listOf(
                Subscription("acct_shop", "shop-b", URI("https://b.example/hooks"), setOf("payment.succeeded", "payment.created"), enabled = true),
                Subscription("acct_shop", "Shop-A", URI("https://a.example/hooks"), setOf("payment.created"), enabled = false),
                Subscription("acct_other", "other", URI("https://other.example/hooks"), setOf("payment.created"), enabled = true),
            ),
        )
    private val payments =
        object : PaymentQueries {
            override fun search(
                filters: PaymentFilters,
                page: PageQuery,
                sort: SortQuery,
            ): Page<Payment> = Page.of(emptyList(), page)

            override fun totalsSince(since: Instant) = listOf(StatusTotal("pending", 2, Money(300))).takeIf { since == calendar.startOf(calendar.today()) }.orEmpty()

            override fun transitionsOf(paymentId: String): List<RecordedTransition> = emptyList()

            override fun conflictsOf(paymentId: String): List<RecordedConflict> = emptyList()

            override fun indeterminateOf(paymentId: String): List<String> = emptyList()

            override fun latestTransitions(
                sources: Set<String>,
                limit: Int,
            ): List<RecordedTransition> = emptyList()

            override fun countTransitionsUpTo(
                sources: Set<String>,
                cap: Int,
            ) = 0L

            override fun latestConflicts(limit: Int): List<RecordedConflict> = emptyList()

            override fun countConflictsUpTo(cap: Int) = 0L

            override fun summaries(ids: Set<String>): Map<String, PaymentSummary> = emptyMap()
        }
    private val deliveries =
        object : DeliveryQueries {
            override fun retryingCount() = 4L

            override fun disabledEndpointCount() = 1L

            override fun conditionOf(endpoint: String) = EndpointCondition(disabledAt, "gone").takeIf { endpoint == "other" }

            override fun eventsOf(paymentId: String): List<RecordedEvent> = emptyList()

            override fun latestAttempts(limit: Int): List<AttemptActivity> = emptyList()

            override fun countAttemptsUpTo(cap: Int) = 0L
        }
    private val keys =
        object : KeyQueries {
            override fun search(
                filters: KeyFilters,
                page: PageQuery,
                sort: SortQuery,
            ): Page<KeyView> = Page.of(emptyList(), page)

            override fun countActive(now: Instant) = 5L
        }
    private val audit =
        object : AuditQueries {
            override fun search(
                filters: AuditFilters,
                page: PageQuery,
                sort: SortQuery,
            ): Page<AuditEntry> = Page.of(emptyList(), page)

            override fun latest(
                kinds: Set<String>,
                limit: Int,
            ): List<AuditEntry> = emptyList()

            override fun countUpTo(
                kinds: Set<String>,
                cap: Int,
            ) = 0L
        }
    private val queue = InMemoryDeliveries().apply { repeat(3) { index -> enqueue("evt_$index", subscriptions.all().first { it.name == "shop-b" }, disabledAt) } }
    private val transactions = CountingTransactions()
    private val queries =
        ConsoleQueries(
            InMemoryPayments(),
            payments,
            deliveries,
            queue,
            keys,
            audit,
            subscriptions,
            CapabilityCatalog(InMemoryProviders(), subscriptions),
            transactions,
            calendar,
            GatewayEnvironment("test"),
        )

    @Test
    fun `the overview counts the business day of the gateway in one read`() {
        assertEquals(ConsoleOverview("test", calendar.today(), listOf(StatusTotal("pending", 2, Money(300))), 4, 1, 5), queries.today())
        assertEquals(1, transactions.opened)
    }

    @Test
    fun `endpoints are filtered by account and text, then sorted by name without regard to case`() {
        val found = queries.search("acct_shop", "EXAMPLE", PageQuery(0, 10), SortQuery.of(null, null))
        assertEquals(listOf("Shop-A", "shop-b"), found.items.map { it.name })
        assertEquals(listOf("payment.created", "payment.succeeded"), found.items.last().events)
        assertEquals(3L, found.items.last().pending)

        val other = queries.search(null, "other", PageQuery(0, 10), SortQuery.of("status", "asc")).items.single()
        assertEquals(disabledAt to "gone", other.disabledAt to other.reason)
    }

    @Test
    fun `endpoints sorted by status put enabled ones first and disabled ones last`() {
        val sorted = queries.search(null, null, PageQuery(0, 10), SortQuery.of("status", "asc"))
        assertEquals(listOf("shop-b", "Shop-A", "other"), sorted.items.map { it.name })
    }

    @Test
    fun `an unknown payment cannot be inspected`() {
        assertEquals(ProblemType.NOT_FOUND, assertFailsWith<OpenFspException> { queries.inspect("pay_unknown") }.type)
    }
}

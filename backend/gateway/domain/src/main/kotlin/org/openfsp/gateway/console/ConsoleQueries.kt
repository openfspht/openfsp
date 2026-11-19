package org.openfsp.gateway.console

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.account.AccountFilter
import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.audit.spi.AuditQueries
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.auth.KeyFilters
import org.openfsp.gateway.auth.KeyView
import org.openfsp.gateway.auth.spi.KeyQueries
import org.openfsp.gateway.capability.api.DescribeCapabilities
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.common.sortedBy
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.console.api.InspectPayments
import org.openfsp.gateway.console.api.ReadOverview
import org.openfsp.gateway.console.api.SearchAudit
import org.openfsp.gateway.console.api.SearchEndpoints
import org.openfsp.gateway.console.api.SearchKeys
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentFilters
import org.openfsp.gateway.payment.spi.PaymentQueries
import org.openfsp.gateway.payment.spi.Payments
import org.openfsp.gateway.webhook.spi.DeliveryQueries
import org.openfsp.gateway.webhook.spi.Subscriptions

@DomainService
class ConsoleQueries(
    private val payments: Payments,
    private val paymentQueries: PaymentQueries,
    private val deliveries: DeliveryQueries,
    private val keys: KeyQueries,
    private val audit: AuditQueries,
    private val subscriptions: Subscriptions,
    private val capabilities: DescribeCapabilities,
    private val transactions: Transactions,
    private val calendar: BusinessCalendar,
    private val environment: GatewayEnvironment,
) : ReadOverview,
    InspectPayments,
    SearchKeys,
    SearchAudit,
    SearchEndpoints {
    override fun today(): ConsoleOverview =
        transactions.reading {
            val day = calendar.today()
            ConsoleOverview(
                environment = environment.name,
                businessDate = day,
                payments = paymentQueries.totalsSince(calendar.startOf(day)),
                retryingDeliveries = deliveries.retryingCount(),
                disabledEndpoints = deliveries.disabledEndpointCount(),
                activeKeys = keys.countActive(calendar.now()),
            )
        }

    override fun search(
        filters: PaymentFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<Payment> = transactions.reading { paymentQueries.search(filters, page, sort) }

    override fun inspect(paymentId: String): PaymentInspection =
        transactions.reading {
            val payment = payments.find(paymentId) ?: throw OpenFspException.notFound()
            PaymentInspection(
                payment = payment,
                automaticSources = capabilities.describe(AccountFilter.Every).firstOrNull { it.id == payment.provider }?.descriptor?.hasAutomaticSource ?: false,
                indeterminate = paymentQueries.indeterminateOf(paymentId),
                transitions = paymentQueries.transitionsOf(paymentId),
                conflicts = paymentQueries.conflictsOf(paymentId),
                events = deliveries.eventsOf(paymentId),
            )
        }

    override fun search(
        filters: KeyFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<KeyView> = transactions.reading { keys.search(filters, page, sort) }

    override fun search(
        filters: AuditFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<AuditEntry> = transactions.reading { audit.search(filters, page, sort) }

    override fun search(
        account: String?,
        query: String?,
        page: PageQuery,
        sort: SortQuery,
    ): Page<EndpointView> =
        transactions.reading {
            val needle = query?.lowercase()
            val matching = subscriptions.all().filter { account == null || it.account == account }.filter { needle == null || needle in it.name.lowercase() || needle in it.url.toString().lowercase() }
            val views =
                matching.map { subscription ->
                    val condition = deliveries.conditionOf(subscription.name)
                    EndpointView(
                        subscription.account,
                        subscription.name,
                        subscription.url.toString(),
                        subscription.events.sorted(),
                        subscription.enabled,
                        subscription.managed,
                        deliveries.pendingCount(subscription.name),
                        condition?.disabledAt,
                        condition?.reason,
                    )
                }
            Page.of(views.sortedBy(sort, ENDPOINT_ORDER, NAME), page)
        }

    private companion object {
        const val NAME = "name"
        private val byName = compareBy<EndpointView> { it.name.lowercase() }
        val ENDPOINT_ORDER: Map<String, Comparator<EndpointView>> =
            mapOf(
                NAME to byName,
                "url" to compareBy { it.url },
                "status" to compareBy<EndpointView> { if (it.disabledAt != null) 2 else if (it.enabled) 0 else 1 }.then(byName),
            )
    }
}

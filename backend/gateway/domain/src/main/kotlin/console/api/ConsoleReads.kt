package org.openfsp.gateway.console.api

import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditFilters
import org.openfsp.gateway.auth.KeyFilters
import org.openfsp.gateway.auth.KeyView
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.console.Activity
import org.openfsp.gateway.console.ActivityKind
import org.openfsp.gateway.console.ConsoleOverview
import org.openfsp.gateway.console.EndpointView
import org.openfsp.gateway.console.PaymentInspection
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentFilters

interface ReadOverview {
    fun today(): ConsoleOverview
}

interface InspectPayments {
    fun search(
        filters: PaymentFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<Payment>

    fun inspect(paymentId: String): PaymentInspection
}

interface SearchKeys {
    fun search(
        filters: KeyFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<KeyView>
}

interface SearchAudit {
    fun search(
        filters: AuditFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<AuditEntry>
}

interface SearchEndpoints {
    fun search(
        account: String?,
        query: String?,
        page: PageQuery,
        sort: SortQuery,
    ): Page<EndpointView>
}

interface ReadActivity {
    fun page(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
        page: PageQuery,
    ): Page<Activity>
}

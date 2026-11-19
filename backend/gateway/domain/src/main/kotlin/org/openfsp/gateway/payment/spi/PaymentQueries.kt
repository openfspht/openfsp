package org.openfsp.gateway.payment.spi

import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentFilters
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payment.StatusTotal
import java.time.Instant

interface PaymentQueries {
    fun search(
        filters: PaymentFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<Payment>

    fun totalsSince(since: Instant): List<StatusTotal>

    fun transitionsOf(paymentId: String): List<RecordedTransition>

    fun conflictsOf(paymentId: String): List<RecordedConflict>

    fun indeterminateOf(paymentId: String): List<String>

    fun latestTransitions(
        sources: Set<String>,
        limit: Int,
    ): List<RecordedTransition>

    fun countTransitionsUpTo(
        sources: Set<String>,
        cap: Int,
    ): Long

    fun latestConflicts(limit: Int): List<RecordedConflict>

    fun countConflictsUpTo(cap: Int): Long

    fun summaries(ids: Set<String>): Map<String, PaymentSummary>
}

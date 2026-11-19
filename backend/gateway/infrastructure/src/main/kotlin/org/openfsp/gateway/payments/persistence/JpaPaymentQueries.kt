package org.openfsp.gateway.payments.persistence

import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.SortQuery
import org.openfsp.gateway.payment.Payment
import org.openfsp.gateway.payment.PaymentFilters
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payment.StatusTotal
import org.openfsp.gateway.payment.spi.PaymentQueries
import org.openfsp.gateway.shared.persistence.contains
import org.openfsp.gateway.shared.persistence.toPage
import org.openfsp.gateway.shared.persistence.toPageable
import org.openfsp.gateway.shared.persistence.toSort
import org.openfsp.protocol.Currency
import org.openfsp.protocol.Money
import org.springframework.data.domain.Limit
import org.springframework.data.jpa.domain.Specification
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JpaPaymentQueries(
    private val payments: PaymentJpaRepository,
    private val entities: PaymentEntityMapper,
    private val transitions: TransitionJpaRepository,
    private val conflicts: ConflictJpaRepository,
    private val indeterminate: IndeterminateOperationJpaRepository,
) : PaymentQueries {
    override fun search(
        filters: PaymentFilters,
        page: PageQuery,
        sort: SortQuery,
    ): Page<Payment> = payments.findAll(specificationOf(filters), page.toPageable(sort.toSort(SORTABLE, CREATED))).toPage(entities::toDomain)

    override fun totalsSince(since: Instant): List<StatusTotal> = payments.countByStatusSince(since).map { StatusTotal(it.status, it.total, Money.htg(it.amount)) }

    override fun transitionsOf(paymentId: String): List<RecordedTransition> = transitions.findByPaymentIdOrderById(paymentId).map(::recorded)

    override fun conflictsOf(paymentId: String): List<RecordedConflict> = conflicts.findByPaymentIdOrderById(paymentId).map(::recorded)

    override fun indeterminateOf(paymentId: String): List<String> = indeterminate.findByPaymentIdOrderById(paymentId).map { it.error }

    override fun latestTransitions(
        sources: Set<String>,
        limit: Int,
    ): List<RecordedTransition> =
        (if (sources.isEmpty()) transitions.findByOrderByIdDesc(Limit.of(limit)) else transitions.findBySourceInOrderByIdDesc(sources, Limit.of(limit))).map(::recorded)

    override fun countTransitionsUpTo(
        sources: Set<String>,
        cap: Int,
    ): Long = if (sources.isEmpty()) transitions.countUpTo(cap) else transitions.countBySourceInUpTo(sources, cap)

    override fun latestConflicts(limit: Int): List<RecordedConflict> = conflicts.findByOrderByIdDesc(Limit.of(limit)).map(::recorded)

    override fun countConflictsUpTo(cap: Int): Long = conflicts.countUpTo(cap)

    override fun summaries(ids: Set<String>): Map<String, PaymentSummary> =
        payments.findAllById(ids).associate { it.id to PaymentSummary(it.id, it.reference, it.provider, Money(it.amount, Currency.valueOf(it.currency)), it.status) }

    private fun recorded(entity: TransitionJpaEntity) = RecordedTransition(entity.id!!, entity.paymentId, entity.fromStatus, entity.toStatus, entity.source, entity.recordedAt)

    private fun recorded(entity: ConflictJpaEntity) = RecordedConflict(entity.id!!, entity.paymentId, entity.source, entity.reported, entity.recordedAt)

    private fun specificationOf(filters: PaymentFilters): Specification<PaymentJpaEntity> =
        Specification { root, _, builder ->
            val conditions =
                listOfNotNull(
                    filters.account?.let { builder.equal(root.get<String>("accountId"), it) },
                    filters.status?.let { builder.equal(root.get<String>("status"), it) },
                    filters.provider?.let { builder.equal(root.get<String>("provider"), it) },
                    filters.query?.let { needle ->
                        val matches =
                            listOfNotNull(
                                builder.contains(root.get("reference"), needle),
                                builder.contains(builder.coalesce(root.get("providerReference"), ""), needle),
                                builder.contains(root.get("id"), needle),
                                centsOf(needle)?.let { builder.equal(root.get<Long>("amount"), it) },
                            )
                        builder.or(*matches.toTypedArray())
                    },
                )
            builder.and(*conditions.toTypedArray())
        }

    private fun centsOf(needle: String): Long? =
        needle
            .replace(" ", "")
            .replace(",", ".")
            .takeIf(AMOUNT::matches)
            ?.let { runCatching { it.toBigDecimal().movePointRight(CENT_DIGITS).longValueExact() }.getOrNull() }

    private companion object {
        const val CREATED = "created_at"
        val SORTABLE = mapOf(CREATED to "createdAt", "reference" to "reference", "provider" to "provider", "amount" to "amount", "status" to "status")
        const val CENT_DIGITS = 2
        val AMOUNT = Regex("""^\d{1,15}(\.\d{1,2})?$""")
    }
}

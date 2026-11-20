package org.openfsp.gateway.console

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.spi.AuditQueries
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.common.PageQuery
import org.openfsp.gateway.common.spi.Transactions
import org.openfsp.gateway.console.api.ReadActivity
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payment.spi.PaymentQueries
import org.openfsp.gateway.webhook.AttemptActivity
import org.openfsp.gateway.webhook.spi.DeliveryQueries

@DomainService
class ConsoleActivity(
    private val payments: PaymentQueries,
    private val deliveries: DeliveryQueries,
    private val audit: AuditQueries,
    private val transactions: Transactions,
) : ReadActivity {
    override fun page(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
        page: PageQuery,
    ): Page<Activity> =
        transactions.readingConsistently {
            val total = minOf(countUpTo(kinds, sources), MAX_DEPTH.toLong())
            val depth = minOf((page.safeNumber + 1) * page.safeSize, MAX_DEPTH)
            Page.of(latest(kinds, sources, depth), page, total)
        }

    private fun countUpTo(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
    ): Long =
        kinds.sumOf { kind ->
            when (kind) {
                ActivityKind.TRANSITION -> payments.countTransitionsUpTo(sources, MAX_DEPTH)
                ActivityKind.CONFLICT -> payments.countConflictsUpTo(MAX_DEPTH)
                ActivityKind.DELIVERY -> deliveries.countAttemptsUpTo(MAX_DEPTH)
                ActivityKind.KEY -> audit.countUpTo(KEY_KINDS, MAX_DEPTH)
                ActivityKind.ADMIN -> audit.countUpTo(ADMIN_KINDS, MAX_DEPTH)
            }
        }

    private fun latest(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
        limit: Int,
    ): List<Activity> {
        val transitions = if (ActivityKind.TRANSITION in kinds) payments.latestTransitions(sources, limit) else emptyList()
        val conflicts = if (ActivityKind.CONFLICT in kinds) payments.latestConflicts(limit) else emptyList()
        val attempts = if (ActivityKind.DELIVERY in kinds) deliveries.latestAttempts(limit) else emptyList()
        val summaries = payments.summaries((transitions.map { it.paymentId } + conflicts.map { it.paymentId } + attempts.map { it.paymentId }).toSet())
        val items =
            transitions.map { transition(it, summaries[it.paymentId]) } +
                conflicts.map { conflict(it, summaries[it.paymentId]) } +
                attempts.map { delivery(it, summaries[it.paymentId]) } +
                auditItems(kinds, limit)
        return items.sortedByDescending { it.at }.take(limit)
    }

    private fun auditItems(
        kinds: Set<ActivityKind>,
        limit: Int,
    ): List<Activity> {
        val wanted = buildSet {
            if (ActivityKind.KEY in kinds) addAll(KEY_KINDS)
            if (ActivityKind.ADMIN in kinds) addAll(ADMIN_KINDS)
        }
        if (wanted.isEmpty()) return emptyList()
        return audit.latest(wanted, limit).map(::auditItem)
    }

    private fun transition(
        transition: RecordedTransition,
        payment: PaymentSummary?,
    ) = Activity(
        id = "transition-${transition.id}",
        at = transition.recordedAt,
        kind = ActivityKind.TRANSITION,
        source = transition.source,
        payment = payment,
        fromStatus = transition.fromStatus,
        toStatus = transition.toStatus,
    )

    private fun conflict(
        conflict: RecordedConflict,
        payment: PaymentSummary?,
    ) = Activity(
        id = "conflict-${conflict.id}",
        at = conflict.recordedAt,
        kind = ActivityKind.CONFLICT,
        source = conflict.source,
        payment = payment,
        subject = conflict.reported,
    )

    private fun delivery(
        activity: AttemptActivity,
        payment: PaymentSummary?,
    ) = Activity(
        id = "delivery-${activity.attempt.id}",
        at = activity.attempt.startedAt,
        kind = ActivityKind.DELIVERY,
        source = null,
        payment = payment,
        eventType = activity.eventType,
        endpoint = activity.endpoint,
        statusCode = activity.attempt.statusCode,
        attempt = activity.attempt.attempt,
        durationMs = activity.attempt.durationMs,
        result = activity.attempt.error,
    )

    private fun auditItem(entry: AuditEntry) =
        Activity(
            id = "audit-${entry.id}",
            at = entry.recordedAt,
            kind = if (entry.kind in KEY_KINDS) ActivityKind.KEY else ActivityKind.ADMIN,
            source = entry.kind,
            payment = null,
            subject = entry.lookupPrefix ?: entry.detail,
            result = entry.result,
        )

    private companion object {
        const val MAX_DEPTH = 1200
        val KEY_KINDS = setOf(AuditKind.KEY_ISSUED, AuditKind.KEY_REVOKED).map { it.wire }.toSet()
        val ADMIN_KINDS = setOf(AuditKind.ADMIN_SIGN_IN, AuditKind.ADMIN_SIGN_IN_FAILURE, AuditKind.WEBHOOK_ADDED, AuditKind.WEBHOOK_REMOVED).map { it.wire }.toSet()
    }
}

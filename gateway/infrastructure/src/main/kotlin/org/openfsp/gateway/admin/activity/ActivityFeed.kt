package org.openfsp.gateway.admin.activity

import org.openfsp.gateway.admin.search.PageQuery
import org.openfsp.gateway.admin.search.PageResource
import org.openfsp.gateway.admin.search.pageOf
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.persistence.audit.AuditRecordJpaEntity
import org.openfsp.gateway.persistence.audit.AuditRecordJpaRepository
import org.openfsp.gateway.persistence.payment.ConflictJpaEntity
import org.openfsp.gateway.persistence.payment.ConflictJpaRepository
import org.openfsp.gateway.persistence.payment.PaymentJpaEntity
import org.openfsp.gateway.persistence.payment.PaymentJpaRepository
import org.openfsp.gateway.persistence.payment.TransitionJpaEntity
import org.openfsp.gateway.persistence.payment.TransitionJpaRepository
import org.openfsp.gateway.persistence.statement.StatementJpaEntity
import org.openfsp.gateway.persistence.statement.StatementJpaRepository
import org.openfsp.gateway.persistence.webhook.AttemptJpaEntity
import org.openfsp.gateway.persistence.webhook.AttemptJpaRepository
import org.openfsp.gateway.persistence.webhook.DeliveryJpaRepository
import org.openfsp.gateway.persistence.webhook.EventJpaRepository
import org.openfsp.protocol.Currency
import org.openfsp.protocol.Money
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class ActivityFeed(
    private val transitions: TransitionJpaRepository,
    private val conflicts: ConflictJpaRepository,
    private val attempts: AttemptJpaRepository,
    private val deliveries: DeliveryJpaRepository,
    private val events: EventJpaRepository,
    private val statements: StatementJpaRepository,
    private val audit: AuditRecordJpaRepository,
    private val payments: PaymentJpaRepository,
) {
    @Transactional(readOnly = true)
    fun page(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
        page: PageQuery,
    ): PageResource<ActivityItem> {
        val total = minOf(count(kinds, sources), MAX_DEPTH.toLong())
        val depth = minOf((page.safeNumber + 1) * page.safeSize, MAX_DEPTH)
        return pageOf(latest(kinds, sources, depth), page, total)
    }

    private fun count(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
    ): Long =
        kinds.sumOf { kind ->
            when (kind) {
                ActivityKind.TRANSITION -> if (sources.isEmpty()) transitions.count() else transitions.countBySourceIn(sources)
                ActivityKind.CONFLICT -> conflicts.count()
                ActivityKind.DELIVERY -> attempts.count()
                ActivityKind.STATEMENT -> statements.count()
                ActivityKind.KEY -> audit.countByKindIn(KEY_KINDS.map { it.wire })
                ActivityKind.ADMIN -> audit.countByKindIn(ADMIN_KINDS.map { it.wire })
            }
        }

    private fun latest(
        kinds: Set<ActivityKind>,
        sources: Set<String>,
        limit: Int,
    ): List<ActivityItem> {
        val window = Limit.of(limit)
        val recentTransitions =
            when {
                ActivityKind.TRANSITION !in kinds -> emptyList()
                sources.isEmpty() -> transitions.findByOrderByIdDesc(window)
                else -> transitions.findBySourceInOrderByIdDesc(sources, window)
            }
        val recentConflicts = if (ActivityKind.CONFLICT in kinds) conflicts.findByOrderByIdDesc(window) else emptyList()
        val recentAttempts = if (ActivityKind.DELIVERY in kinds) attempts.findByOrderByIdDesc(window) else emptyList()
        val deliveryById = deliveries.findAllById(recentAttempts.map { it.deliveryId }.toSet()).associateBy { it.id!! }
        val eventById = events.findAllById(deliveryById.values.map { it.eventId }.toSet()).associateBy { it.id }
        val paymentIds = recentTransitions.map { it.paymentId } + recentConflicts.map { it.paymentId } + eventById.values.map { it.paymentId }
        val paymentById = payments.findAllById(paymentIds.toSet()).associateBy { it.id }
        val items =
            recentTransitions.map { transition(it, paymentById[it.paymentId]) } +
                recentConflicts.map { conflict(it, paymentById[it.paymentId]) } +
                recentAttempts.mapNotNull { attempt ->
                    val delivery = deliveryById[attempt.deliveryId] ?: return@mapNotNull null
                    val event = eventById[delivery.eventId] ?: return@mapNotNull null
                    delivery(attempt, delivery.endpoint, event.type, paymentById[event.paymentId])
                } +
                (if (ActivityKind.STATEMENT in kinds) statements.findByOrderByFetchedAtDesc(window).map(::statement) else emptyList()) +
                auditItems(kinds, window)
        return items.sortedByDescending { it.at }.take(limit)
    }

    private fun auditItems(
        kinds: Set<ActivityKind>,
        window: Limit,
    ): List<ActivityItem> {
        val wanted = buildSet {
            if (ActivityKind.KEY in kinds) addAll(KEY_KINDS)
            if (ActivityKind.ADMIN in kinds) addAll(ADMIN_KINDS)
        }
        if (wanted.isEmpty()) return emptyList()
        return audit.findByKindInOrderByIdDesc(wanted.map { it.wire }, window).map(::auditItem)
    }

    private fun transition(
        entity: TransitionJpaEntity,
        payment: PaymentJpaEntity?,
    ) = ActivityItem(
        id = "transition-${entity.id}",
        at = entity.recordedAt,
        kind = ActivityKind.TRANSITION.wire,
        source = entity.source,
        payment = payment?.let(::summary),
        fromStatus = entity.fromStatus,
        toStatus = entity.toStatus,
    )

    private fun conflict(
        entity: ConflictJpaEntity,
        payment: PaymentJpaEntity?,
    ) = ActivityItem(
        id = "conflict-${entity.id}",
        at = entity.recordedAt,
        kind = ActivityKind.CONFLICT.wire,
        source = entity.source,
        payment = payment?.let(::summary),
        subject = entity.reported,
    )

    private fun delivery(
        attempt: AttemptJpaEntity,
        endpoint: String,
        eventType: String,
        payment: PaymentJpaEntity?,
    ) = ActivityItem(
        id = "delivery-${attempt.id}",
        at = attempt.startedAt,
        kind = ActivityKind.DELIVERY.wire,
        source = null,
        payment = payment?.let(::summary),
        eventType = eventType,
        endpoint = endpoint,
        statusCode = attempt.statusCode,
        attempt = attempt.attempt,
        durationMs = attempt.durationMs,
        result = attempt.error,
    )

    private fun statement(entity: StatementJpaEntity) =
        ActivityItem(
            id = "statement-${entity.id}",
            at = entity.fetchedAt,
            kind = ActivityKind.STATEMENT.wire,
            source = entity.provider,
            payment = null,
            subject = entity.businessDate.toString(),
            result = if (entity.complete) COMPLETE else PARTIAL,
        )

    private fun auditItem(entity: AuditRecordJpaEntity) =
        ActivityItem(
            id = "audit-${entity.id}",
            at = entity.recordedAt,
            kind = if (entity.kind in KEY_KINDS.map { it.wire }) ActivityKind.KEY.wire else ActivityKind.ADMIN.wire,
            source = entity.kind,
            payment = null,
            subject = entity.lookupPrefix ?: entity.detail,
            result = entity.result,
        )

    private fun summary(payment: PaymentJpaEntity) =
        ActivityPayment(payment.id, payment.reference, payment.provider, Money(payment.amount, Currency.valueOf(payment.currency)), payment.status)

    private companion object {
        const val COMPLETE = "complete"
        const val PARTIAL = "partial"
        const val MAX_DEPTH = 1200
        val KEY_KINDS = setOf(AuditKind.KEY_ISSUED, AuditKind.KEY_REVOKED)
        val ADMIN_KINDS = setOf(AuditKind.ADMIN_SIGN_IN, AuditKind.ADMIN_SIGN_IN_FAILURE)
    }
}

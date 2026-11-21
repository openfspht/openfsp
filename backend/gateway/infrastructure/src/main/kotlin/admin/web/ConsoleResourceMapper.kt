package org.openfsp.gateway.admin.web

import org.openfsp.gateway.audit.AuditEntry
import org.openfsp.gateway.auth.KeyView
import org.openfsp.gateway.common.Page
import org.openfsp.gateway.console.Activity
import org.openfsp.gateway.console.ConsoleOverview
import org.openfsp.gateway.console.EndpointView
import org.openfsp.gateway.console.PaymentInspection
import org.openfsp.gateway.payment.PaymentSummary
import org.openfsp.gateway.payment.RecordedConflict
import org.openfsp.gateway.payment.RecordedTransition
import org.openfsp.gateway.payments.web.PaymentResourceMapper
import org.openfsp.gateway.webhook.RecordedAttempt
import org.openfsp.gateway.webhook.RecordedDelivery
import org.openfsp.gateway.webhook.RecordedEvent
import org.openfsp.protocol.PaymentStatus
import org.springframework.stereotype.Component
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@Component
class ConsoleResourceMapper(
    private val payments: PaymentResourceMapper,
    private val json: JsonMapper,
) {
    fun <T, R> toResource(
        page: Page<T>,
        map: (T) -> R,
    ): PageResource<R> = PageResource(page.items.map(map), PageInfo(page.number, page.size, page.totalElements, page.totalPages))

    fun toResource(overview: ConsoleOverview): OverviewResource =
        OverviewResource(
            environment = overview.environment,
            businessDate = overview.businessDate,
            payments = overview.payments.map { OverviewStatusTotal(it.status, it.count, it.amount) },
            retryingDeliveries = overview.retryingDeliveries,
            disabledEndpoints = overview.disabledEndpoints,
            activeKeys = overview.activeKeys,
        )

    fun toResource(inspection: PaymentInspection): PaymentDetailResource =
        PaymentDetailResource(
            payment = payments.toResource(inspection.payment),
            automaticSources = inspection.automaticSources,
            indeterminate = inspection.indeterminate,
            transitions = inspection.transitions.map(::toResource),
            conflicts = inspection.conflicts.map(::toResource),
            events = inspection.events.map(::toResource),
        )

    fun toResource(key: KeyView): KeyResource =
        KeyResource(key.id, key.account, key.principal, key.prefix, key.scopes.map { it.wire }.sorted(), key.createdAt, key.expiresAt, key.lastUsedAt, key.revokedAt, key.demoOnly)

    fun toResource(entry: AuditEntry): AuditRecordResource =
        AuditRecordResource(entry.recordedAt, entry.kind, entry.principalId, entry.lookupPrefix, entry.operation, entry.requestId, entry.result, entry.detail, entry.exchange)

    fun toResource(endpoint: EndpointView): EndpointResource =
        EndpointResource(
            endpoint.account,
            endpoint.name,
            endpoint.url,
            endpoint.events,
            endpoint.enabled,
            endpoint.managed,
            endpoint.pending,
            endpoint.disabledAt,
            endpoint.reason,
        )

    fun toResource(activity: Activity): ActivityItem =
        ActivityItem(
            id = activity.id,
            at = activity.at,
            kind = activity.kind.wire,
            source = activity.source,
            payment = activity.payment?.let(::toResource),
            fromStatus = activity.fromStatus,
            toStatus = activity.toStatus,
            eventType = activity.eventType,
            endpoint = activity.endpoint,
            statusCode = activity.statusCode,
            attempt = activity.attempt,
            durationMs = activity.durationMs,
            subject = activity.subject,
            result = activity.result,
        )

    private fun toResource(summary: PaymentSummary) = ActivityPayment(summary.id, summary.reference, summary.provider, summary.amount, summary.status)

    private fun toResource(transition: RecordedTransition) =
        TransitionResource(transition.fromStatus?.let(PaymentStatus::fromWire), PaymentStatus.fromWire(transition.toStatus)!!, transition.source, transition.recordedAt)

    private fun toResource(conflict: RecordedConflict) = ConflictResource(conflict.source, json.readValue<Map<String, Any>>(conflict.reported), conflict.recordedAt)

    private fun toResource(event: RecordedEvent) = EventResource(event.id, event.type, event.sequence, event.createdAt, event.deliveries.map(::toResource))

    private fun toResource(delivery: RecordedDelivery) = DeliveryResource(delivery.endpoint, delivery.url, delivery.status, delivery.attempts.map(::toResource), delivery.nextAttemptAt)

    private fun toResource(attempt: RecordedAttempt) = AttemptResource(attempt.attempt, attempt.startedAt, attempt.statusCode, attempt.error, attempt.durationMs)
}

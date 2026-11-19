package org.openfsp.gateway.admin.web

import org.openfsp.gateway.audit.HttpExchange
import org.openfsp.protocol.Money
import org.openfsp.protocol.PaymentStatus
import org.openfsp.protocol.api.PaymentResource
import java.time.Instant
import java.time.LocalDate

data class PageInfo(
    val number: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

data class PageResource<T>(
    val data: List<T>,
    val page: PageInfo,
)

data class OverviewStatusTotal(
    val status: String,
    val count: Long,
    val amount: Money,
)

data class OverviewResource(
    val environment: String,
    val businessDate: LocalDate,
    val payments: List<OverviewStatusTotal>,
    val retryingDeliveries: Long,
    val disabledEndpoints: Long,
    val activeKeys: Long,
)

data class EndpointResource(
    val account: String,
    val name: String,
    val url: String,
    val events: List<String>,
    val enabled: Boolean,
    val managed: Boolean,
    val pending: Long,
    val disabledAt: Instant?,
    val reason: String?,
)

data class AuditRecordResource(
    val recordedAt: Instant,
    val kind: String,
    val principalId: String?,
    val lookupPrefix: String?,
    val operation: String,
    val requestId: String?,
    val result: String,
    val detail: String?,
    val exchange: HttpExchange?,
)

data class TransitionResource(
    val fromStatus: PaymentStatus?,
    val toStatus: PaymentStatus,
    val source: String,
    val recordedAt: Instant,
)

data class ConflictResource(
    val source: String,
    val reported: Map<String, Any>,
    val recordedAt: Instant,
)

data class AttemptResource(
    val attempt: Int,
    val startedAt: Instant,
    val statusCode: Int?,
    val error: String?,
    val durationMs: Int,
)

data class DeliveryResource(
    val endpoint: String,
    val url: String,
    val status: String,
    val attempts: List<AttemptResource>,
    val nextAttemptAt: Instant?,
)

data class EventResource(
    val id: String,
    val type: String,
    val sequence: Int,
    val createdAt: Instant,
    val deliveries: List<DeliveryResource>,
)

data class PaymentDetailResource(
    val payment: PaymentResource,
    val automaticSources: Boolean,
    val indeterminate: List<String>,
    val transitions: List<TransitionResource>,
    val conflicts: List<ConflictResource>,
    val events: List<EventResource>,
)

data class KeyResource(
    val id: String,
    val account: String,
    val principal: String,
    val prefix: String,
    val scopes: List<String>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
)

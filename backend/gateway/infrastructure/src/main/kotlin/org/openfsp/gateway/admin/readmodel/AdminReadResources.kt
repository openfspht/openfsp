package org.openfsp.gateway.admin.readmodel

import org.openfsp.gateway.payments.resources.PaymentResource
import org.openfsp.protocol.PaymentStatus
import java.time.Instant

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
    val principal: String,
    val prefix: String,
    val scopes: List<String>,
    val createdAt: Instant,
    val expiresAt: Instant?,
    val lastUsedAt: Instant?,
    val revokedAt: Instant?,
)

package org.openfsp.gateway.webhook

import java.time.Instant

data class RecordedAttempt(
    val id: Long,
    val attempt: Int,
    val startedAt: Instant,
    val statusCode: Int?,
    val error: String?,
    val durationMs: Int,
)

data class RecordedDelivery(
    val endpoint: String,
    val url: String,
    val status: String,
    val attempts: List<RecordedAttempt>,
    val nextAttemptAt: Instant?,
)

data class RecordedEvent(
    val id: String,
    val type: String,
    val sequence: Int,
    val createdAt: Instant,
    val deliveries: List<RecordedDelivery>,
)

data class EndpointCondition(
    val disabledAt: Instant?,
    val reason: String?,
)

data class AttemptActivity(
    val attempt: RecordedAttempt,
    val endpoint: String,
    val eventType: String,
    val paymentId: String,
)

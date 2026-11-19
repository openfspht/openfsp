package org.openfsp.simulator.idempotency

import java.time.Instant

data class IdempotencyScope(
    val operator: String,
    val account: String,
    val principal: String,
    val key: String,
)

data class RecordedResponse(
    val status: Int,
    val body: Any,
    val paymentId: String?,
    val location: String? = null,
)

sealed interface IdempotencyRecord {
    val fingerprint: String

    data class Running(
        override val fingerprint: String,
    ) : IdempotencyRecord

    data class Done(
        override val fingerprint: String,
        val response: RecordedResponse,
        val recordedAt: Instant,
    ) : IdempotencyRecord
}

sealed interface CreationAttempt {
    val response: RecordedResponse

    data class Determined(
        override val response: RecordedResponse,
    ) : CreationAttempt

    data class Undetermined(
        override val response: RecordedResponse,
    ) : CreationAttempt
}

sealed interface IdempotentCreation {
    data object InProgress : IdempotentCreation

    data object Reused : IdempotentCreation

    data class Replayed(
        val response: RecordedResponse,
    ) : IdempotentCreation

    data class Answered(
        val response: RecordedResponse,
    ) : IdempotentCreation
}

package org.openfsp.gateway.idempotency.api

import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse

sealed interface Execution<T> {
    data class Fresh<T>(
        val value: T,
    ) : Execution<T>

    data class Replayed<T>(
        val response: RecordedResponse,
    ) : Execution<T>
}

interface RunIdempotently {
    fun <T> run(
        scope: IdempotencyScope,
        fingerprint: String,
        work: () -> T,
        record: (T) -> RecordedResponse,
    ): Execution<T>

    fun purgeExpired(
        batch: Int,
        maxBatches: Int,
    ): Int
}

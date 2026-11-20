package org.openfsp.gateway.idempotency.api

import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.RequestFingerprint

interface RunIdempotently {
    fun <T> run(
        scope: IdempotencyScope,
        fingerprint: RequestFingerprint,
        work: () -> T,
        record: (T) -> RecordedResponse,
    ): Execution<T>

    fun purgeExpired(
        batch: Int,
        maxBatches: Int,
    ): Int
}

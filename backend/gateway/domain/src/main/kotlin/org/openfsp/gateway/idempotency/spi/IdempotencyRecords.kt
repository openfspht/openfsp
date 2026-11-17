package org.openfsp.gateway.idempotency.spi

import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import java.time.Instant

interface IdempotencyRecords {
    fun insert(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
    ): Boolean

    fun find(scope: IdempotencyScope): IdempotencyRecord?

    fun takeOver(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
        claimedBefore: Instant,
    ): Boolean

    fun complete(
        scope: IdempotencyScope,
        response: RecordedResponse,
    )

    fun release(scope: IdempotencyScope)

    fun purgeCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int
}

package org.openfsp.gateway.idempotency.spi

import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import java.time.Instant

interface IdempotencyRecords {
    fun insert(
        scope: IdempotencyScope,
        fingerprint: String,
        fingerprintVersion: Int,
        at: Instant,
    ): Boolean

    fun find(scope: IdempotencyScope): IdempotencyRecord?

    fun takeOver(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
        claimedBefore: Instant,
        generation: Long,
    ): Boolean

    fun complete(
        scope: IdempotencyScope,
        generation: Long,
        response: RecordedResponse,
    ): Boolean

    fun release(
        scope: IdempotencyScope,
        generation: Long,
    )

    fun discardStale(
        scope: IdempotencyScope,
        createdBefore: Instant,
        fingerprintVersion: Int,
    ): Boolean

    fun purgeCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int
}

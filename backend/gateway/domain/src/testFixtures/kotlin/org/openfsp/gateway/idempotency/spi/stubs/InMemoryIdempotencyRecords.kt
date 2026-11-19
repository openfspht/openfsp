package org.openfsp.gateway.idempotency.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.RequestFingerprint
import org.openfsp.gateway.idempotency.spi.IdempotencyRecords
import java.time.Instant

@Stub
class InMemoryIdempotencyRecords : IdempotencyRecords {
    private data class Row(
        val fingerprint: String,
        val response: RecordedResponse?,
        val createdAt: Instant,
        val claimedAt: Instant,
        val generation: Long = IdempotencyRecord.FIRST_GENERATION,
        val fingerprintVersion: Int = RequestFingerprint.VERSION,
    )

    private val rows = linkedMapOf<IdempotencyScope, Row>()

    val size: Int get() = rows.size

    override fun insert(
        scope: IdempotencyScope,
        fingerprint: String,
        fingerprintVersion: Int,
        at: Instant,
    ): Boolean = rows.putIfAbsent(scope, Row(fingerprint, null, at, at, fingerprintVersion = fingerprintVersion)) == null

    override fun find(scope: IdempotencyScope): IdempotencyRecord? = rows[scope]?.let { IdempotencyRecord(it.fingerprint, it.response, it.createdAt, it.generation, it.fingerprintVersion) }

    override fun takeOver(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
        claimedBefore: Instant,
        generation: Long,
    ): Boolean {
        val row =
            rows[scope]?.takeIf { it.fingerprint == fingerprint && it.response == null && it.claimedAt.isBefore(claimedBefore) && it.generation == generation }
                ?: return false
        rows[scope] = row.copy(claimedAt = at, generation = generation + 1)
        return true
    }

    override fun complete(
        scope: IdempotencyScope,
        generation: Long,
        response: RecordedResponse,
    ): Boolean {
        val row = rows[scope]?.takeIf { it.response == null && it.generation == generation } ?: return false
        rows[scope] = row.copy(response = response)
        return true
    }

    override fun release(
        scope: IdempotencyScope,
        generation: Long,
    ) {
        if (rows[scope]?.let { it.response == null && it.generation == generation } == true) rows.remove(scope)
    }

    override fun discardStale(
        scope: IdempotencyScope,
        createdBefore: Instant,
        fingerprintVersion: Int,
    ): Boolean = rows[scope]?.takeIf { it.createdAt.isBefore(createdBefore) || it.fingerprintVersion != fingerprintVersion }?.let { rows.remove(scope) } != null

    override fun purgeCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int {
        val expired = rows.filterValues { it.createdAt.isBefore(before) }.entries.sortedBy { it.value.createdAt }.take(batch).map { it.key }
        expired.forEach(rows::remove)
        return expired.size
    }
}

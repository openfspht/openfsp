package org.openfsp.gateway.idempotency.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.spi.IdempotencyRecords
import java.time.Instant

@Stub
class InMemoryIdempotencyRecords : IdempotencyRecords {
    private data class Row(
        val fingerprint: String,
        val response: RecordedResponse?,
        val createdAt: Instant,
        val claimedAt: Instant,
    )

    private val rows = linkedMapOf<IdempotencyScope, Row>()

    val size: Int get() = rows.size

    override fun insert(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
    ): Boolean = rows.putIfAbsent(scope, Row(fingerprint, null, at, at)) == null

    override fun find(scope: IdempotencyScope): IdempotencyRecord? = rows[scope]?.let { IdempotencyRecord(it.fingerprint, it.response) }

    override fun takeOver(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
        claimedBefore: Instant,
    ): Boolean {
        val row = rows[scope]?.takeIf { it.fingerprint == fingerprint && it.response == null && it.claimedAt.isBefore(claimedBefore) } ?: return false
        rows[scope] = row.copy(claimedAt = at)
        return true
    }

    override fun complete(
        scope: IdempotencyScope,
        response: RecordedResponse,
    ) {
        rows.computeIfPresent(scope) { _, row -> if (row.response == null) row.copy(response = response) else row }
    }

    override fun release(scope: IdempotencyScope) {
        if (rows[scope]?.response == null) rows.remove(scope)
    }

    override fun purgeCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int {
        val expired = rows.filterValues { it.createdAt.isBefore(before) }.entries.sortedBy { it.value.createdAt }.take(batch).map { it.key }
        expired.forEach(rows::remove)
        return expired.size
    }
}

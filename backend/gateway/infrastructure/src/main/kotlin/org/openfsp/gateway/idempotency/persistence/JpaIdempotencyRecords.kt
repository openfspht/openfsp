package org.openfsp.gateway.idempotency.persistence

import org.openfsp.gateway.idempotency.IdempotencyRecord
import org.openfsp.gateway.idempotency.IdempotencyScope
import org.openfsp.gateway.idempotency.RecordedResponse
import org.openfsp.gateway.idempotency.spi.IdempotencyRecords
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class JpaIdempotencyRecords(
    private val records: IdempotencyRecordRepository,
) : IdempotencyRecords {
    override fun insert(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
    ): Boolean = records.claim(scope.principal, scope.operation, scope.key, fingerprint, at) == 1

    override fun find(scope: IdempotencyScope): IdempotencyRecord? =
        records.findById(scope.toId()).orElse(null)?.let { record ->
            val status = record.responseStatus
            val body = record.responseBody
            IdempotencyRecord(record.fingerprint, if (status != null && body != null) RecordedResponse(status, body) else null)
        }

    override fun takeOver(
        scope: IdempotencyScope,
        fingerprint: String,
        at: Instant,
        claimedBefore: Instant,
    ): Boolean = records.takeOver(scope.toId(), fingerprint, at, claimedBefore) == 1

    override fun complete(
        scope: IdempotencyScope,
        response: RecordedResponse,
    ) {
        records.findById(scope.toId()).filter { it.responseStatus == null }.ifPresent {
            it.responseStatus = response.status
            it.responseBody = response.body
            records.save(it)
        }
    }

    override fun release(scope: IdempotencyScope) {
        records.release(scope.toId())
    }

    override fun purgeCreatedBefore(
        before: Instant,
        batch: Int,
    ): Int = records.deleteBatchCreatedBefore(before, batch)

    private fun IdempotencyScope.toId() = IdempotencyRecordId(principal, operation, key)
}

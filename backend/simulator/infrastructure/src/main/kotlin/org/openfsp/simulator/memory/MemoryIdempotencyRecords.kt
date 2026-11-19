package org.openfsp.simulator.memory

import org.openfsp.simulator.idempotency.IdempotencyRecord
import org.openfsp.simulator.idempotency.IdempotencyScope
import org.openfsp.simulator.idempotency.spi.IdempotencyRecords
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
class MemoryIdempotencyRecords : IdempotencyRecords {
    private val records = ConcurrentHashMap<IdempotencyScope, IdempotencyRecord>()

    override fun compute(
        scope: IdempotencyScope,
        change: (IdempotencyRecord?) -> IdempotencyRecord,
    ): IdempotencyRecord = records.compute(scope) { _, record -> change(record) }!!

    override fun save(
        scope: IdempotencyScope,
        record: IdempotencyRecord,
    ) {
        records[scope] = record
    }

    override fun remove(scope: IdempotencyScope) {
        records.remove(scope)
    }

    override fun purge(before: Instant) {
        records.values.removeIf { it is IdempotencyRecord.Done && it.recordedAt.isBefore(before) }
    }

    override fun clear() {
        records.clear()
    }

    override fun clear(account: String) {
        records.keys.removeIf { it.account == account }
    }
}

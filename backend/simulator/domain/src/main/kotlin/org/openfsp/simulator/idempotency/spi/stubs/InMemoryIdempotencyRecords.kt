package org.openfsp.simulator.idempotency.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.simulator.idempotency.IdempotencyRecord
import org.openfsp.simulator.idempotency.IdempotencyScope
import org.openfsp.simulator.idempotency.spi.IdempotencyRecords
import java.util.concurrent.ConcurrentHashMap

@Stub
class InMemoryIdempotencyRecords : IdempotencyRecords {
    val records = ConcurrentHashMap<IdempotencyScope, IdempotencyRecord>()

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

    override fun clear() {
        records.clear()
    }
}

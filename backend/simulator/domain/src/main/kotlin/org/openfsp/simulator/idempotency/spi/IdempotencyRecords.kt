package org.openfsp.simulator.idempotency.spi

import org.openfsp.simulator.common.spi.SimulationCleanup
import org.openfsp.simulator.idempotency.IdempotencyRecord
import org.openfsp.simulator.idempotency.IdempotencyScope

interface IdempotencyRecords : SimulationCleanup {
    fun compute(
        scope: IdempotencyScope,
        change: (IdempotencyRecord?) -> IdempotencyRecord,
    ): IdempotencyRecord

    fun save(
        scope: IdempotencyScope,
        record: IdempotencyRecord,
    )

    fun remove(scope: IdempotencyScope)
}

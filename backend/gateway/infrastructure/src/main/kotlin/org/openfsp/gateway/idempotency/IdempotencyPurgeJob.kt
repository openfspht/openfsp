package org.openfsp.gateway.idempotency

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class IdempotencyPurgeJob(
    private val idempotency: Idempotency,
) {
    @Scheduled(fixedDelayString = "\${openfsp.idempotency.purge-interval:5m}", initialDelayString = "\${openfsp.idempotency.purge-interval:5m}")
    fun run() {
        purge(BATCH, MAX_BATCHES_PER_RUN)
    }

    fun purge(
        batch: Int,
        maxBatches: Int,
    ): Int {
        var purged = 0
        repeat(maxBatches) {
            val deleted = idempotency.purgeExpired(batch)
            purged += deleted
            if (deleted < batch) return purged
        }
        return purged
    }

    private companion object {
        const val BATCH = 500
        const val MAX_BATCHES_PER_RUN = 20
    }
}

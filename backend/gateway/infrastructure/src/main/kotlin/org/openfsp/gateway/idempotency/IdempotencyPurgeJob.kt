package org.openfsp.gateway.idempotency

import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class IdempotencyPurgeJob(
    private val idempotency: RunIdempotently,
) {
    @Scheduled(fixedDelayString = "\${openfsp.idempotency.purge-interval:5m}", initialDelayString = "\${openfsp.idempotency.purge-interval:5m}")
    fun run() {
        idempotency.purgeExpired(BATCH, MAX_BATCHES_PER_RUN)
    }

    private companion object {
        const val BATCH = 500
        const val MAX_BATCHES_PER_RUN = 20
    }
}

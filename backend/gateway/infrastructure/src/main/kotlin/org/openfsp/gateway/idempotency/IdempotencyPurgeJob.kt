package org.openfsp.gateway.idempotency

import org.openfsp.gateway.idempotency.api.RunIdempotently
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class IdempotencyPurgeJob(
    private val idempotency: RunIdempotently,
) {
    @Scheduled(fixedDelay = 5, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        idempotency.purgeExpired(BATCH, MAX_BATCHES_PER_RUN)
    }

    private companion object {
        const val BATCH = 500
        const val MAX_BATCHES_PER_RUN = 20
    }
}

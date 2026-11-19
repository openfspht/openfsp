package org.openfsp.simulator.idempotency.api

import org.openfsp.simulator.idempotency.CreationAttempt
import org.openfsp.simulator.idempotency.IdempotencyScope
import org.openfsp.simulator.idempotency.IdempotentCreation

interface CreateIdempotently {
    fun create(
        scope: IdempotencyScope,
        fingerprint: String,
        attempt: () -> CreationAttempt,
    ): IdempotentCreation
}

package org.openfsp.gateway.idempotency.api

import org.openfsp.gateway.idempotency.RecordedResponse

sealed interface Execution<T> {
    data class Fresh<T>(
        val value: T,
    ) : Execution<T>

    data class Replayed<T>(
        val response: RecordedResponse,
    ) : Execution<T>
}

package org.openfsp.validator.run.api

import java.util.UUID

enum class RunState {
    PREPARED,
    RUNNING,
    ENDED,
}

fun interface RunStates {
    fun state(id: UUID): RunState
}

package org.openfsp.simulator.emission

import java.time.Duration

data class RunTerms(
    val lifetime: Duration,
    val capacity: Int,
) {
    init {
        require(capacity > 0) { "a simulator admits at least one conformance run" }
    }
}

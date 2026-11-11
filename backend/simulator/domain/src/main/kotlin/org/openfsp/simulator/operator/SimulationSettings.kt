package org.openfsp.simulator.operator

import java.time.Duration

data class SimulationSettings(
    val responseDelay: Duration,
    val settleAfter: Duration,
    val hangDuration: Duration,
    val lateCallbackDelay: Duration,
)

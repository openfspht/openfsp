package org.openfsp.simulator.emission.spi

import org.openfsp.simulator.common.spi.SimulationCleanup
import org.openfsp.simulator.emission.ConformanceRun
import java.time.Instant

interface ConformanceRuns : SimulationCleanup {
    fun admit(
        run: ConformanceRun,
        capacity: Int,
        now: Instant,
    ): ConformanceRun?

    fun find(id: String): ConformanceRun?

    fun withCredential(
        operator: String,
        credential: String,
    ): ConformanceRun?
}

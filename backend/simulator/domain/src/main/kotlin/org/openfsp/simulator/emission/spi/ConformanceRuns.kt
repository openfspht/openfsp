package org.openfsp.simulator.emission.spi

import org.openfsp.simulator.common.spi.SimulationCleanup
import org.openfsp.simulator.emission.ConformanceRun

interface ConformanceRuns : SimulationCleanup {
    fun save(run: ConformanceRun): ConformanceRun

    fun find(id: String): ConformanceRun?

    fun all(): List<ConformanceRun>
}

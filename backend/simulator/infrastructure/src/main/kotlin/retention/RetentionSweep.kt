package org.openfsp.simulator.retention

import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RetentionSweep(
    private val control: ControlSimulation,
    private val clock: SimulationClock,
    private val properties: SimulatorProperties,
) {
    @Scheduled(fixedDelayString = "\${openfsp.simulator.retention-sweep-interval:1m}")
    fun sweep() {
        control.purge(clock.now().minus(properties.retention))
    }
}

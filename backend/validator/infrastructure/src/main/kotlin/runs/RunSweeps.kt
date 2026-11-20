package org.openfsp.validator.runs

import org.openfsp.validator.run.api.SweepRuns
import org.openfsp.validator.shared.configuration.ValidatorProperties
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.stereotype.Component

@Component
class RunSweeps(
    private val runs: SweepRuns,
    private val properties: ValidatorProperties,
) : SchedulingConfigurer {
    override fun configureTasks(registrar: ScheduledTaskRegistrar) = registrar.addFixedDelayTask(runs::sweep, properties.sessionSweepInterval)
}

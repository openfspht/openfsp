package org.openfsp.validator.runs.jobs

import org.openfsp.validator.run.api.SweepRuns
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class RunSweeps(
    private val runs: SweepRuns,
) {
    @Scheduled(fixedDelayString = "\${openfsp.validator.session-sweep-interval:1m}")
    fun sweep() = runs.sweep()
}

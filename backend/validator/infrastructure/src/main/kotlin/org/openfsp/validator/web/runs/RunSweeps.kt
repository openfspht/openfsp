package org.openfsp.validator.web.runs

import org.openfsp.validator.run.api.SweepRuns
import org.openfsp.validator.run.spi.RunAlerts
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RunSweeps(
    private val runs: SweepRuns,
) {
    @Scheduled(fixedDelayString = "\${openfsp.validator.session-sweep-interval:1m}")
    fun sweep() = runs.sweep()
}

@Component
class LoggedRunAlerts : RunAlerts {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun stopped(
        id: UUID,
        cause: Exception,
    ) = log.error("run {} stopped", id, cause)
}

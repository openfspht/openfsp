package org.openfsp.validator.runs

import org.openfsp.validator.run.spi.RunAlerts
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class LoggedRunAlerts : RunAlerts {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun stopped(
        id: UUID,
        cause: Exception,
    ) = log.error("run {} stopped", id, cause)
}

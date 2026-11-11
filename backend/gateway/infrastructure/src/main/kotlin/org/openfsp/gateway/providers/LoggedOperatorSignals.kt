package org.openfsp.gateway.providers

import org.openfsp.gateway.common.spi.OperatorSignals
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class LoggedOperatorSignals : OperatorSignals {
    private val log = LoggerFactory.getLogger("openfsp.operator")

    override fun signal(
        subject: String,
        message: String,
    ) = log.warn("{}: {}", subject, message)
}

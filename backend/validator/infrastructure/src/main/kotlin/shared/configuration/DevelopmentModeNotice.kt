package org.openfsp.validator.shared.configuration

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class DevelopmentModeNotice(
    private val properties: ValidatorProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun announce() {
        if (properties.developmentMode) log.warn("Development mode is on: private and loopback targets are reachable and attestations are signed with a generated key")
    }
}

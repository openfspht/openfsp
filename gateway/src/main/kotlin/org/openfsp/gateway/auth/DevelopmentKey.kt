package org.openfsp.gateway.auth

import org.openfsp.gateway.GatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class DevelopmentKey(
    private val keys: ApiKeys,
    private val properties: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun issueFirstKey() {
        if (!properties.developmentMode || !keys.isEmpty()) return
        val issued = keys.issue("development", setOf(Scope.CAPABILITIES_READ, Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE))
        log.warn("Development API key, shown once: {}", issued.secret)
    }
}

package org.openfsp.gateway.security

import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.configuration.GatewayProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class DevelopmentKeyBootstrap(
    private val keys: ManageKeys,
    private val properties: GatewayProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @EventListener(ApplicationReadyEvent::class)
    fun bootstrap() {
        if (!properties.developmentMode) return
        val provided = properties.bootstrapKey
        if (provided != null) {
            keys.register(PRINCIPAL, provided, SCOPES)
            log.warn("Development mode: the bootstrap API key from the environment is registered")
        } else if (keys.isEmpty()) {
            log.warn("Development API key, shown once: {}", keys.issue(PRINCIPAL, SCOPES).secret)
        }
    }

    private companion object {
        const val PRINCIPAL = "development"
        val SCOPES = setOf(Scope.CAPABILITIES_READ, Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)
    }
}

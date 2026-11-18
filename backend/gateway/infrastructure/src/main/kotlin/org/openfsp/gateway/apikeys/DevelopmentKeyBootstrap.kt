package org.openfsp.gateway.apikeys

import org.openfsp.gateway.account.MerchantAccount
import org.openfsp.gateway.auth.PresentedKey
import org.openfsp.gateway.auth.Scope
import org.openfsp.gateway.auth.api.ManageKeys
import org.openfsp.gateway.shared.configuration.GatewayProperties
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
        if (properties.environment != GatewayProperties.Environment.TEST) return
        val provided = properties.bootstrapKey
        if (provided != null) {
            if (keys.register(MerchantAccount.DEFAULT, PRINCIPAL, provided, SCOPES) != null) {
                log.warn("Test environment: the bootstrap API key {} from the environment is registered", PresentedKey.redact(provided))
            } else {
                log.warn("Test environment: the bootstrap API key {} was revoked and is not registered again", PresentedKey.redact(provided))
            }
        } else if (keys.isEmpty()) {
            log.info("No API key yet: set openfsp.bootstrap-key or issue a key from the admin console")
        }
    }

    private companion object {
        const val PRINCIPAL = "development"
        val SCOPES = setOf(Scope.CAPABILITIES_READ, Scope.PAYMENTS_READ, Scope.PAYMENTS_WRITE)
    }
}

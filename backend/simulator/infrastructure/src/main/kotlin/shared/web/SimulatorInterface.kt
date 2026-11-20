package org.openfsp.simulator.shared.web

import org.openfsp.ui.UserInterface
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class SimulatorInterface {
    @Bean
    fun userInterface(): UserInterface = UserInterface(listOf("/", "/activity", "/requests"), CONTENT_SECURITY_POLICY)

    private companion object {
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; " +
                "connect-src 'self' https://*.sentry.io; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
    }
}

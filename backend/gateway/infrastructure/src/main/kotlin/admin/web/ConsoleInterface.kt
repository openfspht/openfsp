package org.openfsp.gateway.admin.web

import org.openfsp.ui.UserInterface
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class ConsoleInterface {
    @Bean
    fun userInterface(): UserInterface = UserInterface(ROUTES, CONTENT_SECURITY_POLICY)

    private companion object {
        val ROUTES =
            listOf(
                "/",
                "/sign-in",
                "/activity",
                "/payments",
                "/payments/{id}",
                "/providers",
                "/statements",
                "/accounts",
                "/accounts/{id}",
                "/keys",
                "/webhooks",
                "/audit",
                "/configuration",
            )
        const val CONTENT_SECURITY_POLICY =
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: http: https:; font-src 'self'; " +
                "connect-src 'self' https://*.sentry.io; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
    }
}

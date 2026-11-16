package org.openfsp.validator.web.configuration

import org.springframework.boot.EnvironmentPostProcessor
import org.springframework.boot.SpringApplication
import org.springframework.core.Ordered
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.MapPropertySource

class TrustedProxyEnvironment :
    EnvironmentPostProcessor,
    Ordered {
    override fun postProcessEnvironment(
        environment: ConfigurableEnvironment,
        application: SpringApplication,
    ) {
        if (environment.getProperty(BEHIND_TRUSTED_PROXY, Boolean::class.java, false)) {
            environment.propertySources.addLast(MapPropertySource(NAME, mapOf(STRATEGY to FRAMEWORK)))
        }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    companion object {
        const val BEHIND_TRUSTED_PROXY = "openfsp.validator.behind-trusted-proxy"
        const val STRATEGY = "server.forward-headers-strategy"
        private const val NAME = "openfsp-trusted-proxy"
        private const val FRAMEWORK = "framework"
    }
}

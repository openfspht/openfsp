package org.openfsp.validator.shared.configuration

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
            environment.propertySources.addLast(MapPropertySource(NAME, mapOf(STRATEGY to NATIVE, INTERNAL_PROXIES to PRIVATE_NETWORKS)))
        }
    }

    override fun getOrder(): Int = Ordered.LOWEST_PRECEDENCE

    companion object {
        const val BEHIND_TRUSTED_PROXY = "openfsp.validator.behind-trusted-proxy"
        const val STRATEGY = "server.forward-headers-strategy"
        private const val NAME = "openfsp-trusted-proxy"
        const val INTERNAL_PROXIES = "server.tomcat.remoteip.internal-proxies"
        private const val NATIVE = "native"
        private const val PRIVATE_NETWORKS =
            "10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|192\\.168\\.\\d{1,3}\\.\\d{1,3}|172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}|127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|0:0:0:0:0:0:0:1|::1"
    }
}

package org.openfsp.gateway.conformance

import org.openfsp.gateway.conformance.api.PrepareConformance
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Configuration(proxyBeanMethods = false)
class ConformanceConfiguration(
    private val properties: ConformanceProperties,
    gateway: GatewayProperties,
) {
    init {
        check(!properties.enabled || gateway.environment == GatewayProperties.Environment.TEST) {
            "openfsp.conformance.enabled issues test keys on request and is refused outside the test environment"
        }
    }

    @Bean
    fun conformanceSettings() = ConformanceSettings(properties.lifetime, properties.maxActive)
}

@Component
class ConformanceSweep(
    private val conformance: PrepareConformance,
    private val properties: ConformanceProperties,
) {
    @Scheduled(fixedDelayString = "\${openfsp.conformance.sweep-interval:1m}")
    fun run() {
        if (properties.enabled) conformance.sweep()
    }
}

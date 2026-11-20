package org.openfsp.gateway.shared.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.payment.spi.CallbackUrls
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.FilterType

@Configuration(proxyBeanMethods = false)
@ComponentScan(
    basePackages = ["org.openfsp.gateway"],
    useDefaultFilters = false,
    includeFilters = [ComponentScan.Filter(type = FilterType.ANNOTATION, classes = [DomainService::class])],
)
class DomainConfiguration {
    @Bean
    fun gatewayEnvironment(
        properties: GatewayProperties,
        demo: DemoMode,
    ) = GatewayEnvironment(properties.environmentName, demo.active)

    @Bean
    fun callbackUrls(properties: GatewayProperties) =
        CallbackUrls { provider, token -> properties.publicBaseUrl.resolve("/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
}

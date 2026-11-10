package org.openfsp.gateway.configuration

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.auth.GatewayEnvironment
import org.openfsp.gateway.payment.CallbackBase
import org.openfsp.gateway.webhook.DeliveryLimits
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhooks.WebhookProperties
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
    fun gatewayEnvironment(properties: GatewayProperties) = GatewayEnvironment(properties.environmentName)

    @Bean
    fun deliveryLimits(webhooks: WebhookProperties) = DeliveryLimits(webhooks.maxPendingPerEndpoint, webhooks.disableAfterAbandoned)

    @Bean
    fun endpointRules(properties: GatewayProperties) = EndpointRules(plainHttpAllowed = properties.developmentMode)

    @Bean
    fun callbackBase(properties: GatewayProperties) =
        CallbackBase { provider, token -> properties.publicBaseUrl.resolve("/callbacks/$provider" + (token?.let { "/$it" } ?: "")) }
}

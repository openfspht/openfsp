package org.openfsp.gateway.webhooks

import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.KeyRotationPolicy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {
    @Bean
    fun keyRotationPolicy(webhooks: WebhookProperties) = KeyRotationPolicy(webhooks.rotateKeyEvery.takeUnless { it.isZero || it.isNegative })

    @Bean
    fun endpointRules(properties: GatewayProperties) = EndpointRules(properties.developmentMode)
}

package org.openfsp.gateway.webhooks

import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.webhook.DeliveryLimits
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.KeyRotationPolicy
import org.openfsp.http.GuardedHttpClients
import org.openfsp.protocol.webhook.DeliverySchedule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class WebhookConfiguration {
    @Bean
    fun deliverySchedule(): DeliverySchedule = DeliverySchedule()

    @Bean
    fun guardedHttpClients(): GuardedHttpClients = GuardedHttpClients()

    @Bean
    fun deliveryLimits(webhooks: WebhookProperties) = DeliveryLimits(webhooks.maxPendingPerEndpoint, webhooks.disableAfterAbandoned)

    @Bean
    fun keyRotationPolicy(webhooks: WebhookProperties) = KeyRotationPolicy(webhooks.rotateKeyEvery.takeUnless { it.isZero || it.isNegative })

    @Bean
    fun endpointRules(properties: GatewayProperties) = EndpointRules(plainHttpAllowed = properties.developmentMode, privateAddressesAllowed = properties.developmentMode)
}

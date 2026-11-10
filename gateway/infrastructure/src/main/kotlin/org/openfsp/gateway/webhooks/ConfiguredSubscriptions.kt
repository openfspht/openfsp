package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.springframework.stereotype.Component

@Component
class ConfiguredSubscriptions(
    properties: WebhookProperties,
) : Subscriptions {
    private val subscriptions = properties.endpoints.map { (name, endpoint) -> Subscription(name, endpoint.url, endpoint.events, endpoint.enabled) }

    override fun all(): List<Subscription> = subscriptions
}

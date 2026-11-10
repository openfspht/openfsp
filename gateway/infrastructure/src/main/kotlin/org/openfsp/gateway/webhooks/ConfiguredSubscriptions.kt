package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.SubscriptionStore
import org.openfsp.gateway.webhook.spi.Subscriptions
import org.springframework.stereotype.Component

@Component
class ConfiguredSubscriptions(
    properties: WebhookProperties,
    private val store: SubscriptionStore,
) : Subscriptions {
    private val configured = properties.endpoints.map { (name, endpoint) -> Subscription(name, endpoint.url, endpoint.events, endpoint.enabled) }

    override fun all(): List<Subscription> = configured + store.all()
}

package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.Subscriptions

@Stub
class FixedSubscriptions(
    private val subscriptions: List<Subscription> = emptyList(),
) : Subscriptions {
    override fun all(): List<Subscription> = subscriptions
}

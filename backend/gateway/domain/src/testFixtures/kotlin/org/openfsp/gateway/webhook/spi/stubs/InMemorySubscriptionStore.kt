package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.Subscription
import org.openfsp.gateway.webhook.spi.DuplicateSubscription
import org.openfsp.gateway.webhook.spi.SubscriptionStore

@Stub
class InMemorySubscriptionStore : SubscriptionStore {
    private val saved = linkedMapOf<String, Subscription>()

    override fun all(): List<Subscription> = saved.values.toList()

    override fun save(subscription: Subscription) {
        if (subscription.name in saved) throw DuplicateSubscription()
        saved[subscription.name] = subscription
    }

    override fun delete(name: String): Boolean = saved.remove(name) != null
}

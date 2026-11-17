package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.Subscription

interface SubscriptionStore {
    fun all(): List<Subscription>

    fun save(subscription: Subscription)

    fun delete(name: String): Boolean
}

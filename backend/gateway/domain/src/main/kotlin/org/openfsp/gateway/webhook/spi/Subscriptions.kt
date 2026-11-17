package org.openfsp.gateway.webhook.spi

import org.openfsp.gateway.webhook.Subscription

interface Subscriptions {
    fun all(): List<Subscription>
}

package org.openfsp.gateway.webhook.api

import org.openfsp.gateway.webhook.Subscription
import java.net.URI

interface ManageSubscriptions {
    fun add(
        name: String,
        url: URI,
        events: Set<String>,
    ): Subscription

    fun remove(name: String): Boolean

    fun enable(name: String): Boolean
}

package org.openfsp.gateway.webhook.spi

import java.time.Instant

interface EndpointHealth {
    fun isDisabled(endpoint: String): Boolean

    fun succeeded(endpoint: String)

    fun abandoned(endpoint: String): Int

    fun disable(
        endpoint: String,
        reason: String,
        at: Instant,
    )

    fun forget(endpoint: String)

    fun enable(endpoint: String)
}

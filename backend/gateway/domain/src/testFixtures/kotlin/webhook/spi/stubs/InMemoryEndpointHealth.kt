package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.spi.EndpointHealth
import java.time.Instant

@Stub
class InMemoryEndpointHealth : EndpointHealth {
    private val abandoned = mutableMapOf<String, Int>()
    val disabled = mutableMapOf<String, String>()

    override fun isDisabled(endpoint: String): Boolean = endpoint in disabled

    override fun succeeded(endpoint: String) {
        abandoned[endpoint] = 0
    }

    override fun abandoned(endpoint: String): Int = abandoned.merge(endpoint, 1, Int::plus)!!

    override fun disable(
        endpoint: String,
        reason: String,
        at: Instant,
    ) {
        disabled[endpoint] = reason
    }

    override fun forget(endpoint: String) {
        abandoned.remove(endpoint)
        disabled.remove(endpoint)
    }

    override fun enable(endpoint: String) {
        abandoned.remove(endpoint)
        disabled.remove(endpoint)
    }
}

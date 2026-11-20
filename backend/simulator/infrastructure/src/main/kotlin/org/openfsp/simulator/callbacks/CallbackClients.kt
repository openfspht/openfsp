package org.openfsp.simulator.callbacks

import org.openfsp.http.GuardedHttpClients
import org.openfsp.http.HttpClientLimits
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class CallbackClients {
    fun create(
        callbacks: SimulatorProperties.Callbacks,
        trustedHosts: Set<String>,
    ): RestClient {
        val guard = CallbackAddressGuard(trustedHosts, callbacks.privateAddressesAllowed)
        val http =
            GuardedHttpClients.create(
                guard::resolve,
                HttpClientLimits(
                    connectTimeout = callbacks.connectTimeout,
                    socketTimeout = callbacks.readTimeout,
                    connectionRequestTimeout = callbacks.connectTimeout,
                    maxConnections = MAX_CONNECTIONS,
                    maxConnectionsPerRoute = MAX_CONNECTIONS_PER_ROUTE,
                ),
            )
        return RestClient.builder().requestFactory(HttpComponentsClientHttpRequestFactory(http)).build()
    }

    private companion object {
        const val MAX_CONNECTIONS = 64
        const val MAX_CONNECTIONS_PER_ROUTE = 8
    }
}

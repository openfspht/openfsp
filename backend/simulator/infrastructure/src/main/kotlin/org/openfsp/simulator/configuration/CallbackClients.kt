package org.openfsp.simulator.configuration

import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import org.openfsp.simulator.dialects.CallbackAddressGuard
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient

object CallbackClients {
    private const val MAX_CONNECTIONS = 64
    private const val MAX_CONNECTIONS_PER_ROUTE = 8

    fun create(
        callbacks: SimulatorProperties.Callbacks,
        trustedHosts: Set<String>,
    ): RestClient {
        val connections =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setDnsResolver(CallbackAddressGuard(trustedHosts, callbacks.privateAddressesAllowed))
                .setMaxConnTotal(MAX_CONNECTIONS)
                .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                .setDefaultConnectionConfig(
                    ConnectionConfig
                        .custom()
                        .setConnectTimeout(Timeout.of(callbacks.connectTimeout))
                        .setSocketTimeout(Timeout.of(callbacks.readTimeout))
                        .build(),
                ).build()
        val http =
            HttpClients
                .custom()
                .setConnectionManager(connections)
                .setDefaultRequestConfig(
                    RequestConfig
                        .custom()
                        .setConnectionRequestTimeout(Timeout.of(callbacks.connectTimeout))
                        .setResponseTimeout(Timeout.of(callbacks.readTimeout))
                        .build(),
                ).disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
                .build()
        return RestClient.builder().requestFactory(HttpComponentsClientHttpRequestFactory(http)).build()
    }
}

package org.openfsp.http

import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.Timeout
import java.net.InetAddress
import java.time.Duration

data class HttpClientLimits(
    val connectTimeout: Duration,
    val socketTimeout: Duration,
    val connectionRequestTimeout: Duration? = null,
    val maxConnections: Int? = null,
    val maxConnectionsPerRoute: Int? = null,
    val reusesConnections: Boolean = true,
    val decompresses: Boolean = true,
)

object GuardedHttpClients {
    fun create(
        resolve: (String) -> Array<InetAddress>,
        limits: HttpClientLimits,
    ): CloseableHttpClient {
        val connections =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setDnsResolver(resolverOf(resolve))
                .setDefaultConnectionConfig(
                    ConnectionConfig
                        .custom()
                        .setConnectTimeout(Timeout.of(limits.connectTimeout))
                        .setSocketTimeout(Timeout.of(limits.socketTimeout))
                        .build(),
                )
        limits.maxConnections?.let(connections::setMaxConnTotal)
        limits.maxConnectionsPerRoute?.let(connections::setMaxConnPerRoute)
        val requests = RequestConfig.custom().setResponseTimeout(Timeout.of(limits.socketTimeout))
        limits.connectionRequestTimeout?.let { requests.setConnectionRequestTimeout(Timeout.of(it)) }
        val client =
            HttpClients
                .custom()
                .setConnectionManager(connections.build())
                .setDefaultRequestConfig(requests.build())
                .disableRedirectHandling()
                .disableAutomaticRetries()
                .disableCookieManagement()
                .disableAuthCaching()
        if (!limits.reusesConnections) client.setConnectionReuseStrategy { _, _, _ -> false }
        if (!limits.decompresses) client.disableContentCompression()
        return client.build()
    }

    private fun resolverOf(resolve: (String) -> Array<InetAddress>): DnsResolver =
        object : DnsResolver {
            override fun resolve(host: String): Array<InetAddress> = resolve(host)

            override fun resolveCanonicalHostname(host: String): String = host
        }
}

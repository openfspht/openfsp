package org.openfsp.validator.web.runs

import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.BasicClassicHttpRequest
import org.apache.hc.core5.util.Timeout
import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.run.spi.TargetConnection
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.time.Duration

class PinnedTransport(
    private val target: PinnedTarget,
    resolveOther: (String) -> List<InetAddress>,
    timeout: Duration,
) : TargetConnection {
    private val resolver =
        object : DnsResolver {
            override fun resolve(host: String): Array<InetAddress> =
                (if (host.equals(target.host, ignoreCase = true)) target.addresses else resolveOther(host)).toTypedArray()

            override fun resolveCanonicalHostname(host: String): String = host
        }

    private val http: CloseableHttpClient =
        HttpClients
            .custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder
                    .create()
                    .setDnsResolver(resolver)
                    .setDefaultConnectionConfig(
                        ConnectionConfig
                            .custom()
                            .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                            .setSocketTimeout(Timeout.of(timeout))
                            .build(),
                    ).build(),
            ).setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(timeout)).build())
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .disableContentCompression()
            .build()

    override fun send(request: OutgoingRequest): Exchange {
        val message =
            BasicClassicHttpRequest(request.method, URI.create(request.url)).apply {
                request.headers.forEach { (name, value) -> setHeader(name, value) }
                if (request.body != null || request.method.uppercase() in WITH_BODY) entity = ByteArrayEntity(request.body ?: ByteArray(0), null)
            }
        return try {
            http.execute(message) { response ->
                val headers = response.headers.groupBy({ it.name.lowercase() }, { it.value })
                Exchange(request, response.code, headers, response.entity?.let(EntityUtils::toByteArray) ?: ByteArray(0))
            }
        } catch (exception: IOException) {
            throw TransportException("${request.method} ${ExchangeRedaction.pairs(request.url)} failed: ${exception.message ?: exception.javaClass.simpleName}", exception)
        }
    }

    override fun close() = http.close()

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
        val WITH_BODY = setOf("POST", "PUT", "PATCH")
    }
}

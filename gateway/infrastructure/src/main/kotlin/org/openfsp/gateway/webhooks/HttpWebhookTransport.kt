package org.openfsp.gateway.webhooks

import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.util.Timeout
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.spi.WebhookTransport
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.net.InetAddress
import java.time.Duration

@Component
class HttpWebhookTransport internal constructor(
    private val signer: () -> WebhookSigner,
    addresses: EndpointAddresses,
) : WebhookTransport,
    AutoCloseable {
    @Autowired
    constructor(signers: WebhookSigners, addresses: EndpointAddresses) : this(signers::signer, addresses)

    private val pinnedResolver =
        object : DnsResolver {
            override fun resolve(host: String): Array<InetAddress> = addresses.resolve(host).toTypedArray()

            override fun resolveCanonicalHostname(host: String): String = host
        }

    private val http: CloseableHttpClient =
        HttpClients
            .custom()
            .setConnectionManager(
                PoolingHttpClientConnectionManagerBuilder
                    .create()
                    .setDnsResolver(pinnedResolver)
                    .setMaxConnTotal(MAX_CONNECTIONS)
                    .setMaxConnPerRoute(MAX_CONNECTIONS)
                    .setDefaultConnectionConfig(
                        ConnectionConfig
                            .custom()
                            .setConnectTimeout(Timeout.of(CONNECT_TIMEOUT))
                            .setSocketTimeout(Timeout.of(RESPONSE_TIMEOUT))
                            .build(),
                    ).build(),
            ).setDefaultRequestConfig(RequestConfig.custom().setResponseTimeout(Timeout.of(RESPONSE_TIMEOUT)).build())
            .setConnectionReuseStrategy { _, _, _ -> false }
            .disableRedirectHandling()
            .disableAutomaticRetries()
            .disableCookieManagement()
            .disableAuthCaching()
            .build()

    override fun send(
        delivery: Delivery,
        attempt: Int,
    ): TransportResult {
        val started = System.nanoTime()
        val elapsed = { Duration.ofNanos(System.nanoTime() - started).toMillis() }
        return try {
            val headers = signer().sign(delivery.url.toString(), delivery.body, delivery.eventId, delivery.eventType)
            val request =
                HttpPost(delivery.url).apply {
                    entity = ByteArrayEntity(delivery.body, null)
                    headers.forEach { (name, value) -> setHeader(name, value) }
                    setHeader(WebhookHeaders.DELIVERY_ATTEMPT, attempt.toString())
                }
            http.executeOpen(null, request, null).use { response ->
                val retryAfter =
                    response
                        .takeIf { it.code in THROTTLED }
                        ?.getFirstHeader(RETRY_AFTER)
                        ?.value
                        ?.toLongOrNull()
                        ?.let(Duration::ofSeconds)
                TransportResult(response.code, null, retryAfter, elapsed())
            }
        } catch (exception: Exception) {
            TransportResult(null, exception.javaClass.simpleName, null, elapsed())
        }
    }

    override fun close() = http.close()

    private companion object {
        const val RETRY_AFTER = "Retry-After"
        const val MAX_CONNECTIONS = 64
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(7)
        val THROTTLED = setOf(429, 503)
    }
}

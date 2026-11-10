package org.openfsp.gateway.webhooks

import org.apache.hc.client5.http.DnsResolver
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.http.ClassicHttpResponse
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class HttpWebhookTransport internal constructor(
    private val signer: () -> WebhookSigner,
    addresses: EndpointAddresses,
    private val budget: Duration,
) : WebhookTransport,
    AutoCloseable {
    @Autowired
    constructor(signers: WebhookSigners, addresses: EndpointAddresses) : this(signers::signer, addresses, BUDGET)

    private val deadlines =
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "webhook-deadlines").apply { isDaemon = true } }

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
            val deadline = deadlines.schedule({ request.cancel() }, budget.toMillis(), TimeUnit.MILLISECONDS)
            try {
                val response = http.executeOpen(null, request, null)
                val result = TransportResult(response.code, null, retryAfter(response), elapsed())
                // Closing an open response drains its body; cancelling first drops the connection instead.
                request.cancel()
                runCatching { response.close() }
                result
            } finally {
                deadline.cancel(false)
            }
        } catch (exception: Exception) {
            TransportResult(null, exception.javaClass.simpleName, null, elapsed())
        }
    }

    private fun retryAfter(response: ClassicHttpResponse): Duration? =
        response
            .takeIf { it.code in THROTTLED }
            ?.getFirstHeader(RETRY_AFTER)
            ?.value
            ?.toLongOrNull()
            ?.let(Duration::ofSeconds)

    override fun close() {
        deadlines.shutdownNow()
        http.close()
    }

    private companion object {
        const val RETRY_AFTER = "Retry-After"
        const val MAX_CONNECTIONS = 64
        val BUDGET: Duration = Duration.ofSeconds(10)
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(7)
        val THROTTLED = setOf(429, 503)
    }
}

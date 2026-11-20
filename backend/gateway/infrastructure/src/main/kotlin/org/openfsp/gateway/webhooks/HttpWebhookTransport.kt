package org.openfsp.gateway.webhooks

import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.spi.WebhookTransport
import org.openfsp.http.GuardedHttpClients
import org.openfsp.http.HttpClientLimits
import org.openfsp.protocol.RetryAfter
import org.openfsp.protocol.webhook.WebhookHeaders
import org.openfsp.protocol.webhook.WebhookSigner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Component
class HttpWebhookTransport internal constructor(
    private val signer: () -> WebhookSigner,
    addresses: EndpointAddresses,
    private val budget: Duration,
    private val clock: Clock,
) : WebhookTransport,
    AutoCloseable {
    @Autowired
    constructor(
        signers: WebhookSigners,
        addresses: EndpointAddresses,
        clock: Clock,
    ) : this(signers::signer, addresses, BUDGET, clock)

    private val deadlines =
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "webhook-deadlines").apply { isDaemon = true } }

    private val http: CloseableHttpClient =
        GuardedHttpClients.create(
            { host -> addresses.resolve(host).toTypedArray() },
            HttpClientLimits(
                connectTimeout = CONNECT_TIMEOUT,
                socketTimeout = RESPONSE_TIMEOUT,
                maxConnections = MAX_CONNECTIONS,
                maxConnectionsPerRoute = MAX_CONNECTIONS,
                reusesConnections = false,
            ),
        )

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
            .getFirstHeader(RETRY_AFTER)
            ?.value
            ?.let { RetryAfter.parse(it, clock.instant()) }

    override fun close() {
        deadlines.shutdownNow()
        http.close()
    }

    internal companion object {
        const val MAX_CONNECTIONS = 64
        private const val RETRY_AFTER = "Retry-After"
        private val BUDGET: Duration = Duration.ofSeconds(10)
        private val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        private val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(7)
    }
}

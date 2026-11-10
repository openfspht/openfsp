package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.spi.WebhookTransport
import org.openfsp.protocol.webhook.WebhookHeaders
import org.springframework.stereotype.Component
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

@Component
class HttpWebhookTransport(
    private val keys: SigningKeys,
) : WebhookTransport {
    private val http =
        HttpClient
            .newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    override fun send(
        delivery: Delivery,
        attempt: Int,
    ): TransportResult {
        val started = System.nanoTime()
        val elapsed = { Duration.ofNanos(System.nanoTime() - started).toMillis() }
        return try {
            val headers = keys.signer().sign(delivery.url.toString(), delivery.body, delivery.eventId, delivery.eventType)
            val request =
                HttpRequest
                    .newBuilder(delivery.url)
                    .timeout(RESPONSE_TIMEOUT)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(delivery.body))
                    .apply { headers.forEach { (name, value) -> header(name, value) } }
                    .header(WebhookHeaders.DELIVERY_ATTEMPT, attempt.toString())
                    .build()
            val response = http.send(request, HttpResponse.BodyHandlers.discarding())
            val retryAfter =
                response
                    .takeIf { it.statusCode() in THROTTLED }
                    ?.headers()
                    ?.firstValue("Retry-After")
                    ?.map { it.toLongOrNull() }
                    ?.orElse(null)
                    ?.let(Duration::ofSeconds)
            TransportResult(response.statusCode(), null, retryAfter, elapsed())
        } catch (exception: Exception) {
            TransportResult(null, exception.javaClass.simpleName, null, elapsed())
        }
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val RESPONSE_TIMEOUT: Duration = Duration.ofSeconds(7)
        val THROTTLED = setOf(429, 503)
    }
}

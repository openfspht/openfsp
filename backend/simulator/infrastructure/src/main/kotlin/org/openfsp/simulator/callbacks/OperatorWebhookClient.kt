package org.openfsp.simulator.callbacks

import org.openfsp.protocol.RetryAfter
import org.openfsp.simulator.emission.DeliveryResult
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import java.time.Clock

@Component
class OperatorWebhookClient(
    private val client: RestClient,
    private val retryAfter: RetryAfter,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun get(url: String): Boolean =
        runCatching {
            client
                .get()
                .uri(url)
                .retrieve()
                .toBodilessEntity()
                .statusCode
        }.onSuccess { if (!it.is2xxSuccessful) log.warn("Callback to {} answered {}", url, it.value()) }
            .onFailure { log.warn("Callback to {} failed: {}", url, it.message) }
            .getOrNull()
            ?.is2xxSuccessful == true

    fun post(
        url: String,
        body: ByteArray,
        headers: Map<String, String> = emptyMap(),
    ): Boolean =
        runCatching {
            client
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { http -> headers.forEach(http::set) }
                .body(body)
                .retrieve()
                .toBodilessEntity()
                .statusCode
        }.onSuccess { if (!it.is2xxSuccessful) log.warn("Callback to {} answered {}", url, it.value()) }
            .onFailure { log.warn("Callback to {} failed: {}", url, it.message) }
            .getOrNull()
            ?.is2xxSuccessful == true

    fun deliver(
        url: String,
        body: ByteArray,
        headers: Map<String, String>,
    ): DeliveryResult =
        runCatching {
            client
                .post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .headers { http -> headers.forEach(http::set) }
                .body(body)
                .exchange { _, response -> DeliveryResult(response.statusCode.value(), response.headers.getFirst(HttpHeaders.RETRY_AFTER)?.let { retryAfter.parse(it, clock.instant()) }) }
        }.onSuccess { if (!it.succeeded) log.warn("Delivery to {} answered {}", url, it.status) }
            .onFailure { log.warn("Delivery to {} failed: {}", url, it.message) }
            .getOrElse { DeliveryResult(null) }

}

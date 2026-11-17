package org.openfsp.simulator.callbacks

import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class OperatorWebhookClient(
    private val client: RestClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

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
}

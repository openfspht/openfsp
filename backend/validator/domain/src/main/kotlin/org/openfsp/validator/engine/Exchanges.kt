package org.openfsp.validator.engine

import org.openfsp.server.ExchangeRedaction
import org.openfsp.validator.json.Json
import org.openfsp.validator.json.spi.JsonCodec
import java.time.Instant

data class OutgoingRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: ByteArray?,
)

data class Exchange(
    val request: OutgoingRequest,
    val status: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
    val receivedAt: Instant? = null,
) {
    val text: String get() = String(body, Charsets.UTF_8)

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun json(codec: JsonCodec): Json? = codec.parse(body)

    fun redacted(secrets: Collection<String>): RedactedExchange =
        RedactedExchange(
            request =
                RedactedRequest(
                    method = request.method,
                    url = mask(ExchangeRedaction.pairs(request.url), secrets),
                    headers = ExchangeRedaction.headers(request.headers).mapValues { mask(it.value, secrets) },
                    body = request.body?.let { mask(ExchangeRedaction.body(String(it, Charsets.UTF_8)), secrets) },
                ),
            response =
                RedactedResponse(
                    status = status,
                    headers = ExchangeRedaction.headers(headers.mapValues { it.value.joinToString(", ") }).mapValues { mask(it.value, secrets) },
                    body = mask(ExchangeRedaction.body(text), secrets),
                ),
        )

    private fun mask(
        text: String,
        secrets: Collection<String>,
    ): String = secrets.filter { it.isNotEmpty() }.fold(text) { masked, secret -> masked.replace(secret, ExchangeRedaction.MASK) }
}

data class RedactedRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String>,
    val body: String?,
)

data class RedactedResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
)

data class RedactedExchange(
    val request: RedactedRequest,
    val response: RedactedResponse,
)

class TransportException(
    message: String,
    cause: Throwable,
) : RuntimeException(message, cause)

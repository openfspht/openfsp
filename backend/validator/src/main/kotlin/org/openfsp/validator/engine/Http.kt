package org.openfsp.validator.engine

import org.openfsp.protocol.ExchangeRedaction
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

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
) {
    val text: String get() = String(body, Charsets.UTF_8)

    fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value?.firstOrNull()

    fun json(mapper: JsonMapper): JsonNode? = runCatching { mapper.readTree(body) }.getOrNull()?.takeUnless { it.isMissingNode }

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

class Http(
    private val timeout: Duration,
) {
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    fun send(request: OutgoingRequest): Exchange {
        val builder =
            HttpRequest
                .newBuilder(URI.create(request.url))
                .timeout(timeout)
                .method(request.method, request.body?.let { HttpRequest.BodyPublishers.ofByteArray(it) } ?: HttpRequest.BodyPublishers.noBody())
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        return try {
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray())
            Exchange(request, response.statusCode(), response.headers().map(), response.body())
        } catch (exception: IOException) {
            throw TransportException("${request.method} ${ExchangeRedaction.pairs(request.url)} failed: ${exception.message ?: exception.javaClass.simpleName}", exception)
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw TransportException("${request.method} ${request.url} was interrupted", exception)
        }
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(5)
    }
}

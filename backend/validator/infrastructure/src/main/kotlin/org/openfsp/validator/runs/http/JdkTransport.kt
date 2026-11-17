package org.openfsp.validator.runs.http

import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.engine.spi.Transport
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class JdkTransport(
    private val timeout: Duration,
) : Transport {
    private val client: HttpClient =
        HttpClient
            .newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build()

    override fun send(request: OutgoingRequest): Exchange {
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

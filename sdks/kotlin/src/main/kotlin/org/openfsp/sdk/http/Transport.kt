package org.openfsp.sdk.http

import org.openfsp.sdk.TransportException
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.TreeMap
import java.net.http.HttpRequest as JdkRequest
import java.net.http.HttpResponse as JdkResponse

class HttpRequest(
    val method: String,
    val uri: URI,
    headers: Map<String, String>,
    val body: ByteArray?,
    val timeout: Duration,
) {
    val headers: Map<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(headers) }

    fun header(name: String): String? = headers[name]

    override fun toString(): String = "HttpRequest($method $uri)"
}

class HttpResponse(
    val status: Int,
    headers: Map<String, String>,
    val body: ByteArray,
) {
    val headers: Map<String, String> = TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER).apply { putAll(headers) }

    fun header(name: String): String? = headers[name]

    override fun toString(): String = "HttpResponse($status)"
}

fun interface Transport {
    fun send(request: HttpRequest): HttpResponse
}

/** Never follows redirects: the Authorization header would follow them to another host. */
class JdkTransport
    @JvmOverloads
    constructor(
        private val client: HttpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build(),
    ) : Transport {
        override fun send(request: HttpRequest): HttpResponse {
            val jdk =
                JdkRequest
                    .newBuilder(request.uri)
                    .timeout(request.timeout)
                    .method(request.method, request.body?.let(JdkRequest.BodyPublishers::ofByteArray) ?: JdkRequest.BodyPublishers.noBody())
                    .apply { request.headers.forEach(::header) }
                    .build()
            val response =
                try {
                    client.send(jdk, JdkResponse.BodyHandlers.ofByteArray())
                } catch (timeout: HttpTimeoutException) {
                    throw TransportException("${request.method} ${request.uri.path} timed out after ${request.timeout}", timeout)
                } catch (failure: IOException) {
                    throw TransportException("${request.method} ${request.uri.path} failed: ${failure.javaClass.simpleName}", failure)
                } catch (interrupted: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw TransportException("${request.method} ${request.uri.path} was interrupted", interrupted)
                }
            return HttpResponse(response.statusCode(), response.headers().map().mapValues { it.value.joinToString(", ") }, response.body() ?: ByteArray(0))
        }

        private companion object {
            val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(10)
        }
    }

package org.openfsp.validator.runs.http

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.openfsp.http.GuardedHttpClients
import org.openfsp.http.HttpClientLimits
import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.validator.engine.Exchange
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.run.spi.TargetConnection
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class TransportLimits(
    val requestTimeout: Duration,
    val maxResponseBytes: Int,
    val runDeadline: Instant,
    val clock: Clock,
)

class PinnedTransport(
    private val target: PinnedTarget,
    resolveOther: (String) -> List<InetAddress>,
    private val limits: TransportLimits,
    clients: GuardedHttpClients,
) : TargetConnection {
    private val timeout = limits.requestTimeout

    private val http: CloseableHttpClient =
        clients.create(
            { host -> (if (host.equals(target.host, ignoreCase = true)) target.addresses else resolveOther(host)).toTypedArray() },
            HttpClientLimits(connectTimeout = CONNECT_TIMEOUT, socketTimeout = timeout, decompresses = false),
        )

    override fun send(request: OutgoingRequest): Exchange {
        val left = Duration.between(limits.clock.instant(), limits.runDeadline)
        if (!left.isPositive) throw TransportException("${request.method} ${ExchangeRedaction.pairs(request.url)} was not sent: the run reached its total duration", IOException("run deadline"))
        val message =
            HttpUriRequestBase(request.method, URI.create(request.url)).apply {
                request.headers.forEach { (name, value) -> setHeader(name, value) }
                if (request.body != null || request.method.uppercase() in WITH_BODY) entity = ByteArrayEntity(request.body ?: ByteArray(0), null)
            }
        val deadline = minOf(timeout, left)
        CompletableFuture.delayedExecutor(deadline.toMillis(), TimeUnit.MILLISECONDS).execute { message.cancel() }
        return try {
            http.execute(message) { response ->
                val headers = response.headers.groupBy({ it.name.lowercase() }, { it.value })
                Exchange(request, response.code, headers, response.entity?.content?.use(::bounded) ?: ByteArray(0))
            }
        } catch (exception: IOException) {
            if (message.isCancelled) throw TransportException("${request.method} ${ExchangeRedaction.pairs(request.url)} did not complete within ${deadline.toMillis()}ms", exception)
            throw TransportException("${request.method} ${ExchangeRedaction.pairs(request.url)} failed: ${exception.message ?: exception.javaClass.simpleName}", exception)
        }
    }

    override fun close() = http.close()

    private fun bounded(input: InputStream): ByteArray {
        val bytes = input.readNBytes(limits.maxResponseBytes + 1)
        if (bytes.size > limits.maxResponseBytes) throw IOException("the response is larger than ${limits.maxResponseBytes} bytes")
        return bytes
    }

    private companion object {
        val WITH_BODY = setOf("POST", "PUT", "PATCH")
    }
}

package org.openfsp.validator.runs.http

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.WebhookRecorder
import org.openfsp.validator.shared.json.JacksonJsonCodec
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

object WebhookBodies {
    const val MAX_BYTES = 1 shl 20

    fun readBounded(input: InputStream): ByteArray? {
        val bytes = input.readNBytes(MAX_BYTES + 1)
        return bytes.takeIf { it.size <= MAX_BYTES }
    }
}

class LocalWebhookReceiver(
    bind: String,
    port: Int,
    publicUrl: String?,
) : AutoCloseable {
    private val executor: ExecutorService = Executors.newVirtualThreadPerTaskExecutor()
    private val server: HttpServer = HttpServer.create(InetSocketAddress(bind, port), 0)
    private val origin: String
    val inbox: WebhookRecorder

    init {
        val listening = server.address.port
        val url = publicUrl ?: "http://${if (':' in bind) "[$bind]" else bind}:$listening$DEFAULT_PATH"
        val parsed = URI.create(url)
        origin = "${parsed.scheme}://${parsed.rawAuthority}"
        inbox = WebhookRecorder(url, JacksonJsonCodec)
        server.executor = executor
        server.createContext("/", ::handle)
        server.start()
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val body = WebhookBodies.readBounded(exchange.requestBody)
            if (body == null) {
                exchange.sendResponseHeaders(PAYLOAD_TOO_LARGE, -1)
                return
            }
            val target = origin + exchange.requestURI.rawPath + (exchange.requestURI.rawQuery?.let { "?$it" } ?: "")
            val reply = inbox.receive(exchange.requestMethod, target, exchange.requestHeaders.toMap(), body)
            if (!reply.delay.isZero) Thread.sleep(reply.delay)
            reply.headers.forEach { (name, value) -> exchange.responseHeaders.add(name, value) }
            exchange.sendResponseHeaders(reply.status, if (reply.body.isEmpty()) -1 else reply.body.size.toLong())
            if (reply.body.isNotEmpty()) exchange.responseBody.write(reply.body)
        }
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }

    companion object {
        const val DEFAULT_PATH = "/webhooks"
        private const val PAYLOAD_TOO_LARGE = 413
    }
}

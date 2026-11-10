package org.openfsp.gateway.providers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList

data class Recorded(
    val method: String,
    val path: String,
    val query: String?,
    val authorization: String?,
    val body: String,
)

class RecordedOperator : AutoCloseable {
    val requests = CopyOnWriteArrayList<Recorded>()
    private val answers = mutableMapOf<String, ArrayDeque<Pair<Int, String>>>()

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange -> answer(exchange) }
            start()
        }

    val url: URI get() = URI("http://127.0.0.1:${server.address.port}")

    fun on(
        path: String,
        status: Int,
        body: String,
    ) {
        answers.getOrPut(path) { ArrayDeque() }.addLast(status to body)
    }

    private fun answer(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        requests += Recorded(exchange.requestMethod, path, exchange.requestURI.query, exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.readAllBytes().decodeToString())
        val queue = answers[path]
        val (status, body) = if (queue != null && queue.size > 1) queue.removeFirst() else queue?.firstOrNull() ?: (404 to "{}")
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}

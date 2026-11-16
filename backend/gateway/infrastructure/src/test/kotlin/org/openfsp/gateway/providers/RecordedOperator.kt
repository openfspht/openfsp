package org.openfsp.gateway.providers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
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
    private val answers = mutableMapOf<String, ArrayDeque<Answer>>()

    private data class Answer(
        val status: Int,
        val body: String,
        val headers: Map<String, String>,
        val delay: Duration,
    )

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
        headers: Map<String, String> = emptyMap(),
        delay: Duration = Duration.ZERO,
    ) {
        answers.getOrPut(path) { ArrayDeque() }.addLast(Answer(status, body, headers, delay))
    }

    private fun answer(exchange: HttpExchange) {
        val path = exchange.requestURI.path
        requests += Recorded(exchange.requestMethod, path, exchange.requestURI.query, exchange.requestHeaders.getFirst("Authorization"), exchange.requestBody.readAllBytes().decodeToString())
        val queue = answers[path]
        val answer = if (queue != null && queue.size > 1) queue.removeFirst() else queue?.firstOrNull() ?: Answer(404, "{}", emptyMap(), Duration.ZERO)
        if (!answer.delay.isZero) Thread.sleep(answer.delay)
        val bytes = answer.body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        answer.headers.forEach { (name, value) -> exchange.responseHeaders.set(name, value) }
        exchange.sendResponseHeaders(answer.status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    override fun close() = server.stop(0)
}

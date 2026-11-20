package org.openfsp.gateway

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

data class ReceivedWebhook(
    val headers: Map<String, String>,
    val body: ByteArray,
)

class WebhookReceiver : AutoCloseable {
    val received = CopyOnWriteArrayList<ReceivedWebhook>()

    @Volatile
    var respondWith: Int = 204

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/webhooks") { exchange ->
                received +=
                    ReceivedWebhook(
                        exchange.requestHeaders.entries.associate { (name, values) -> name.lowercase() to values.first() },
                        exchange.requestBody.readAllBytes(),
                    )
                exchange.sendResponseHeaders(respondWith, -1)
                exchange.close()
            }
            start()
        }

    val url: String get() = "http://127.0.0.1:${server.address.port}/webhooks"

    override fun close() = server.stop(0)
}

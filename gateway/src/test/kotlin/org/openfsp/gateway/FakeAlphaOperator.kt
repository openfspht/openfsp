package org.openfsp.gateway

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class FakeAlphaOperator : AutoCloseable {
    val calls = AtomicInteger()
    val statuses = ConcurrentHashMap<String, String>()

    @Volatile
    var failWith: Int? = null

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/mock_alpha/v1/payments") { exchange ->
                calls.incrementAndGet()
                val body = exchange.requestBody.readAllBytes().decodeToString()
                val (status, response) =
                    failWith?.let { it to """{"code":"INTERNAL","message":"down"}""" }
                        ?: if (exchange.requestMethod == "POST") {
                            val orderId = Regex("\"order_id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                            statuses.putIfAbsent(orderId, "PENDING")
                            201 to payment(orderId, statuses.getValue(orderId))
                        } else {
                            val orderId = exchange.requestURI.path.substringAfterLast('/')
                            200 to payment(orderId, statuses[orderId] ?: "PENDING")
                        }
                exchange.responseHeaders.add("Content-Type", "application/json")
                val bytes = response.toByteArray()
                exchange.sendResponseHeaders(status, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/mock_alpha/v1"

    fun callback(
        orderId: String,
        status: String,
        amount: String = "1250.00",
        reason: String? = null,
    ): Pair<String, String> {
        val body =
            """{"payment_id":"MA-1","order_id":"$orderId","status":"$status","amount":"$amount"${reason?.let { ",\"reason\":\"$it\"" } ?: ""}}"""
        return body to "sha256=${sign(body)}"
    }

    private fun payment(
        orderId: String,
        status: String,
    ) = """{"payment_id":"MA-1","order_id":"$orderId","status":"$status","amount":"1250.00","approval":"PAYER_APPROVAL","expires_in":900}"""

    private fun sign(body: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(CALLBACK_SECRET.toByteArray(), "HmacSHA256"))
        return HexFormat.of().formatHex(mac.doFinal(body.toByteArray()))
    }

    override fun close() = server.stop(0)

    companion object {
        const val CALLBACK_SECRET = "mock_alpha_callback_secret"
    }
}

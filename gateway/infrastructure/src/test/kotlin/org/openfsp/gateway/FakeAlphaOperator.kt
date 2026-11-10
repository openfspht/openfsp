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
    private val amounts = ConcurrentHashMap<String, String>()
    val callbackUrls = ConcurrentHashMap<String, String>()
    val statementLines = java.util.concurrent.CopyOnWriteArrayList<String>()

    @Volatile
    var statementClosed: Boolean = false

    @Volatile
    var failWith: Int? = null

    @Volatile
    var expiresIn: Long = 900

    @Volatile
    var errorMessage: String = "down"

    @Volatile
    var loseCreationAnswers: Boolean = false

    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/mock_epsilon/v1/statements") { exchange ->
                val day = exchange.requestURI.path.substringAfterLast('/')
                val bytes = """{"business_date":"$day","closed":$statementClosed,"entries":[${statementLines.joinToString(",")}]}""".toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            createContext("/") { exchange ->
                calls.incrementAndGet()
                val body = exchange.requestBody.readAllBytes().decodeToString()
                val (status, response) =
                    failWith?.let { it to """{"code":"INTERNAL","message":"$errorMessage"}""" }
                        ?: if (exchange.requestMethod == "POST") {
                            val orderId = Regex("\"order_id\":\"([^\"]+)\"").find(body)!!.groupValues[1]
                            statuses.putIfAbsent(orderId, "PENDING")
                            amounts.putIfAbsent(orderId, Regex("\"amount\":\"([^\"]+)\"").find(body)!!.groupValues[1])
                            Regex("\"callback_url\":\"([^\"]+)\"").find(body)?.let { callbackUrls[orderId] = it.groupValues[1] }
                            if (loseCreationAnswers) 500 to """{"code":"INTERNAL","message":"$errorMessage"}""" else 201 to payment(orderId, statuses.getValue(orderId))
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

    val baseUrl: String get() = url("mock_alpha")

    fun url(operator: String): String = "http://127.0.0.1:${server.address.port}/$operator/v1"

    fun statementLine(
        reference: String,
        orderId: String?,
        amount: String,
        bookedAt: String,
    ) {
        statementLines += """{"reference":"$reference","order_id":${orderId?.let { "\"$it\"" } ?: "null"},"type":"PAYMENT","amount":"$amount","booked_at":"$bookedAt"}"""
    }

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
    ) = """{"payment_id":"MA-1","order_id":"$orderId","status":"$status","amount":"${amounts[orderId] ?: "1250.00"}","approval":"PAYER_APPROVAL","expires_in":$expiresIn}"""

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

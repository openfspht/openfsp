package org.openfsp.validator.runs.http

import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.spi.SimulatedPaymentState
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.time.Duration
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpSimulatorControlTest {
    private var payments: List<String> = emptyList()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/control/payments") { exchange ->
                val query = exchange.requestURI.rawQuery.split('&').associate { it.substringBefore('=') to URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
                val size = query.getValue("size").toInt().coerceAtMost(MAX_PAGE)
                val page = query["page"]?.toInt() ?: 0
                val matching = payments.filter { it.contains(query.getValue("q")) }
                val body =
                    """{"data":[${matching.drop(page * size).take(size).joinToString(",")}],""" +
                        """"page":{"number":$page,"size":$size,"total_elements":${matching.size},"total_pages":${(matching.size + size - 1) / size}}}"""
                val bytes = body.toByteArray()
                exchange.responseHeaders.add("Content-Type", "application/json")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
    private val control = HttpSimulatorControls().at("http://127.0.0.1:${server.address.port}", Duration.ofSeconds(5))

    @AfterTest
    fun stop() {
        server.stop(0)
    }

    private fun payment(
        reference: String,
        field: String = "order_id",
        outcome: String? = null,
    ) = """{"payment_id":"pay-$reference","$field":"$reference","outcome":${outcome?.let { "\"$it\"" } ?: "null"},"callbacks_sent":2,"amount":"10.00"}"""

    @Test
    fun `the payment is found past the first page of references that contain it`() {
        payments = (0 until 150).map { payment("ref-1-$it") } + payment("ref-1", outcome = "SUCCESS")
        assertEquals(SimulatedPaymentState("pay-ref-1", settled = true, callbacksSent = 2), control.payment("mock_openfsp", "ref-1"))
    }

    @Test
    fun `a payment named by reference instead of order id is read too`() {
        payments = listOf(payment("ref-2", field = "reference"))
        assertEquals(SimulatedPaymentState("pay-ref-2", settled = false, callbacksSent = 2), control.payment("mock_openfsp", "ref-2"))
        assertNull(control.payment("mock_openfsp", "ref-3"))
    }

    private companion object {
        const val MAX_PAGE = 100
    }
}

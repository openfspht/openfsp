package org.openfsp.validator.web.runs

import com.sun.net.httpserver.HttpServer
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.web.configuration.ValidatorProperties
import org.openfsp.validator.web.problems.ValidatorException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PinnedTransportTest {
    private val hosts = CopyOnWriteArrayList<String>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                hosts += exchange.requestHeaders.getFirst("Host")
                val body = """{"path":"${exchange.requestURI.path}"}""".toByteArray()
                exchange.sendResponseHeaders(200, body.size.toLong())
                exchange.responseBody.use { it.write(body) }
            }
            start()
        }
    private val port = server.address.port
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val lookups = CopyOnWriteArrayList<String>()
    private val rebinding =
        HostLookup { host ->
            lookups += host
            if (lookups.count { it == host } == 1) listOf(loopback) else listOf(InetAddress.getByName("10.0.0.1"))
        }

    @AfterTest
    fun stop() = server.stop(0)

    private fun get(
        transport: PinnedTransport,
        host: String,
        path: String,
    ) = transport.send(OutgoingRequest("GET", "http://$host:$port$path", emptyMap(), null))

    @Test
    fun `the checked addresses serve every request of the run even when the name later resolves elsewhere`() {
        val guard = TargetGuard(ValidatorProperties(developmentMode = true), rebinding)
        val pinned = guard.pin(URI("http://rebind.test:$port"))
        assertEquals(listOf(loopback), pinned.addresses)

        PinnedTransport(pinned, guard::resolve, Duration.ofSeconds(5)).use { transport ->
            repeat(3) { index -> assertEquals(200, get(transport, "rebind.test", "/v1/things/$index").status) }
        }

        assertEquals(listOf("rebind.test"), lookups)
        assertEquals(List(3) { "rebind.test:$port" }, hosts)
    }

    @Test
    fun `another host is resolved and checked at each connection and refused when it is not public`() {
        val guard = TargetGuard(ValidatorProperties(developmentMode = false), { listOf(loopback) })
        val pinned = PinnedTarget("target.test", listOf(loopback))

        PinnedTransport(pinned, guard::resolve, Duration.ofSeconds(5)).use { transport ->
            assertEquals(200, get(transport, "target.test", "/v1/things").status)
            val refused = assertFailsWith<TransportException> { get(transport, "elsewhere.test", "/v1/things") }
            assertEquals(true, refused.message?.contains("not a public address"), refused.message)
        }
        assertFailsWith<ValidatorException> { guard.pin(URI("http://rebind.test")) }
    }
}

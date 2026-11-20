package org.openfsp.validator.runs.http

import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.time.Clock
import java.net.InetSocketAddress
import java.net.URI
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.openfsp.validator.engine.OutgoingRequest
import org.openfsp.validator.engine.TransportException
import org.openfsp.validator.problem.ValidatorException
import org.openfsp.validator.shared.configuration.ValidatorProperties

class PinnedTransportTest {
    private val hosts = CopyOnWriteArrayList<String>()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                hosts += exchange.requestHeaders.getFirst("Host")
                if (exchange.requestURI.path == "/large") {
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { out -> repeat(64) { out.write(ByteArray(1024)) } }
                    return@createContext
                }
                if (exchange.requestURI.path == "/drip") {
                    exchange.sendResponseHeaders(200, 0)
                    exchange.responseBody.use { out ->
                        repeat(40) {
                            out.write(' '.code)
                            out.flush()
                            Thread.sleep(100)
                        }
                    }
                    return@createContext
                }
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

    private fun limits(
        maxResponseBytes: Int = 1 shl 20,
        requestTimeout: Duration = Duration.ofSeconds(5),
        runDeadline: Instant = Instant.now().plusSeconds(60),
    ) = TransportLimits(requestTimeout, maxResponseBytes, runDeadline, Clock.systemUTC())

    @Test
    fun `a response larger than the bound is refused`() {
        PinnedTransport(PinnedTarget("target.test", listOf(loopback)), { listOf(loopback) }, limits(maxResponseBytes = 16 * 1024)).use { transport ->
            val refused = assertFailsWith<TransportException> { get(transport, "target.test", "/large") }
            assertEquals(true, refused.message?.contains("larger than"), refused.message)
        }
    }

    @Test
    fun `a response that drips past the request deadline is abandoned`() {
        PinnedTransport(PinnedTarget("target.test", listOf(loopback)), { listOf(loopback) }, limits(requestTimeout = Duration.ofMillis(800))).use { transport ->
            val started = Instant.now()
            val refused = assertFailsWith<TransportException> { get(transport, "target.test", "/drip") }
            assertEquals(true, refused.message?.contains("did not complete"), refused.message)
            assertTrue(Duration.between(started, Instant.now()) < Duration.ofSeconds(3))
        }
    }

    @Test
    fun `no request is sent once the run reaches its total duration`() {
        PinnedTransport(PinnedTarget("target.test", listOf(loopback)), { listOf(loopback) }, limits(runDeadline = Instant.now().minusSeconds(1))).use { transport ->
            val refused = assertFailsWith<TransportException> { get(transport, "target.test", "/v1/things") }
            assertEquals(true, refused.message?.contains("total duration"), refused.message)
        }
        assertEquals(emptyList(), hosts)
    }

    private fun get(
        transport: PinnedTransport,
        host: String,
        path: String,
    ) = transport.send(OutgoingRequest("GET", "http://$host:$port$path", emptyMap(), null))

    @Test
    fun `the checked addresses serve every request of the run even when the name later resolves elsewhere`() {
        val guard = TargetGuard(ValidatorProperties(developmentMode = true), rebinding, Clock.systemUTC())
        val pinned = guard.pin(URI("http://rebind.test:$port"))
        assertEquals(listOf(loopback), pinned.addresses)

        PinnedTransport(pinned, guard::resolve, limits()).use { transport ->
            repeat(3) { index -> assertEquals(200, get(transport, "rebind.test", "/v1/things/$index").status) }
        }

        assertEquals(listOf("rebind.test"), lookups)
        assertEquals(List(3) { "rebind.test:$port" }, hosts)
    }

    @Test
    fun `another host is resolved and checked at each connection and refused when it is not public`() {
        val guard = TargetGuard(ValidatorProperties(developmentMode = false), { listOf(loopback) }, Clock.systemUTC())
        val pinned = PinnedTarget("target.test", listOf(loopback))

        PinnedTransport(pinned, guard::resolve, limits()).use { transport ->
            assertEquals(200, get(transport, "target.test", "/v1/things").status)
            val refused = assertFailsWith<TransportException> { get(transport, "elsewhere.test", "/v1/things") }
            assertEquals(true, refused.message?.contains("not a public address"), refused.message)
        }
        assertFailsWith<ValidatorException> { guard.pin(URI("http://rebind.test")) }
    }
}

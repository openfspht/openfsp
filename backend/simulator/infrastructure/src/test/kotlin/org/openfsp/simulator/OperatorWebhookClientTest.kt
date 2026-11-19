package org.openfsp.simulator

import com.sun.net.httpserver.HttpServer
import org.openfsp.protocol.RetryAfter
import org.openfsp.simulator.callbacks.CallbackClients
import org.openfsp.simulator.callbacks.OperatorWebhookClient
import org.openfsp.simulator.shared.configuration.SimulatorProperties
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperatorWebhookClientTest {
    private val received = AtomicInteger()
    private val server =
        HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/callbacks") { exchange ->
                received.incrementAndGet()
                exchange.sendResponseHeaders(NO_CONTENT, -1)
                exchange.close()
            }
            start()
        }
    private val url = "http://127.0.0.1:${server.address.port}/callbacks"

    @AfterTest
    fun stop() = server.stop(0)

    private fun client(
        privateAddressesAllowed: Boolean = false,
        trustedHosts: Set<String> = emptySet(),
        readTimeout: Duration = Duration.ofSeconds(10),
    ) = OperatorWebhookClient(
        CallbackClients.create(SimulatorProperties.Callbacks(Duration.ofSeconds(1), readTimeout, privateAddressesAllowed), trustedHosts),
        RetryAfter(),
        Clock.systemUTC(),
    )

    @Test
    fun `a callback to a loopback or private address is refused before it is sent`() {
        assertFalse(client().post(url, "{}".toByteArray()))
        assertFalse(client().post("http://10.0.0.1/callbacks", "{}".toByteArray()))
        assertFalse(client().post("http://169.254.169.254/latest/meta-data", "{}".toByteArray()))
        assertEquals(0, received.get())
    }

    @Test
    fun `a trusted callback host or the development flag lets a private address through`() {
        assertTrue(client(trustedHosts = setOf("localhost")).post("http://localhost:${server.address.port}/callbacks", "{}".toByteArray()))
        assertTrue(client(privateAddressesAllowed = true).post(url, "{}".toByteArray()))
        assertEquals(2, received.get())
    }

    @Test
    fun `a callback URL that never answers gives up after the read timeout`() {
        ServerSocket(0).use { silent ->
            val started = System.nanoTime()
            val sent = client(privateAddressesAllowed = true, readTimeout = Duration.ofMillis(300)).post("http://127.0.0.1:${silent.localPort}/callbacks", "{}".toByteArray())
            assertFalse(sent)
            assertTrue(Duration.ofNanos(System.nanoTime() - started) < Duration.ofSeconds(5))
        }
    }

    @Test
    fun `a redirect is not followed`() {
        server.createContext("/moved") { exchange ->
            exchange.responseHeaders.add("Location", url)
            exchange.sendResponseHeaders(TEMPORARY_REDIRECT, -1)
            exchange.close()
        }
        assertFalse(client(privateAddressesAllowed = true).post("http://127.0.0.1:${server.address.port}/moved", "{}".toByteArray()))
        assertEquals(0, received.get())
    }

    private companion object {
        const val NO_CONTENT = 204
        const val TEMPORARY_REDIRECT = 307
    }
}

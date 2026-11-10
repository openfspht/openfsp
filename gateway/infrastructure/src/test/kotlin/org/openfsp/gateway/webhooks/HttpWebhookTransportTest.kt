package org.openfsp.gateway.webhooks

import org.openfsp.gateway.WebhookReceiver
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.TransportResult
import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import org.openfsp.protocol.webhook.WebhookSigner
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.security.KeyPairGenerator
import java.time.Duration
import kotlin.concurrent.thread
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpWebhookTransportTest {
    private val receiver = WebhookReceiver()
    private val resolver = StaticHostResolver()
    private val signer = WebhookSigner(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().private, "whk_test")

    private fun transport(
        privateAddressesAllowed: Boolean,
        budget: Duration = Duration.ofSeconds(10),
    ) = HttpWebhookTransport(
        { signer },
        EndpointAddresses(resolver, EndpointRules(plainHttpAllowed = true, privateAddressesAllowed = privateAddressesAllowed)),
        budget,
    )

    private fun slowServer(answer: (OutputStream) -> Unit): ServerSocket {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    socket.getInputStream().read(ByteArray(BUFFER))
                    answer(socket.getOutputStream())
                }
            }
        }
        return server
    }

    private fun timed(block: () -> TransportResult): Pair<TransportResult, Duration> {
        val started = System.nanoTime()
        val result = block()
        return result to Duration.ofNanos(System.nanoTime() - started)
    }

    private fun delivery(url: String) = Delivery(1, "evt_1", "payment.created", "shop", URI(url), 0, """{"id":"evt_1"}""".toByteArray())

    private val port get() = URI(receiver.url).port

    @AfterTest
    fun stop() = receiver.close()

    @Test
    fun `a delivery to a loopback address is refused before connecting`() {
        val result = transport(privateAddressesAllowed = false).use { it.send(delivery(receiver.url), 1) }
        assertEquals(null, result.statusCode)
        assertEquals("UnsafeEndpointAddressException", result.error)
        assertTrue(receiver.received.isEmpty())
    }

    @Test
    fun `a host that now resolves to a private address is refused at delivery`() {
        resolver.addresses["rebinding.example"] = listOf(InetAddress.getByName("10.0.0.5"))
        val result = transport(privateAddressesAllowed = false).use { it.send(delivery("http://rebinding.example:$port/webhooks"), 1) }
        assertEquals("UnsafeEndpointAddressException", result.error)
    }

    @Test
    fun `the connection goes to the checked address, not to a second resolution`() {
        resolver.addresses["shop.example"] = listOf(InetAddress.getByName("127.0.0.1"))
        val result = transport(privateAddressesAllowed = true).use { it.send(delivery("http://shop.example:$port/webhooks"), 1) }
        assertEquals(204, result.statusCode)
        assertEquals("shop.example:$port", receiver.received.single().headers["host"])
    }

    @Test
    fun `a response trickled byte by byte is cut at the delivery budget`() {
        slowServer { out ->
            "HTTP/1.1 204 No Content\r\n\r\n".forEach { char ->
                out.write(char.code)
                out.flush()
                Thread.sleep(TRICKLE_MILLIS)
            }
        }.use { server ->
            val (result, took) = timed { transport(privateAddressesAllowed = true, budget = BUDGET).use { it.send(delivery("http://127.0.0.1:${server.localPort}/webhooks"), 1) } }
            assertEquals(null, result.statusCode)
            assertTrue(took < BUDGET.plus(GRACE), "took $took")
        }
    }

    @Test
    fun `a response body that never ends is not waited for`() {
        slowServer { out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\npartial".toByteArray())
            out.flush()
            Thread.sleep(STALL_MILLIS)
        }.use { server ->
            val (result, took) = timed { transport(privateAddressesAllowed = true, budget = BUDGET).use { it.send(delivery("http://127.0.0.1:${server.localPort}/webhooks"), 1) } }
            assertEquals(200, result.statusCode)
            assertTrue(took < BUDGET, "took $took")
        }
    }

    private companion object {
        const val BUFFER = 8192
        const val TRICKLE_MILLIS = 200L
        const val STALL_MILLIS = 5_000L
        val BUDGET: Duration = Duration.ofSeconds(1)
        val GRACE: Duration = Duration.ofMillis(500)
    }
}

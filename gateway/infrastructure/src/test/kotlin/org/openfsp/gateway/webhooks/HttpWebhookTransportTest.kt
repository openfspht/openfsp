package org.openfsp.gateway.webhooks

import org.openfsp.gateway.WebhookReceiver
import org.openfsp.gateway.webhook.Delivery
import org.openfsp.gateway.webhook.EndpointAddresses
import org.openfsp.gateway.webhook.EndpointRules
import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import org.openfsp.protocol.webhook.WebhookSigner
import java.net.InetAddress
import java.net.URI
import java.security.KeyPairGenerator
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HttpWebhookTransportTest {
    private val receiver = WebhookReceiver()
    private val resolver = StaticHostResolver()
    private val signer = WebhookSigner(KeyPairGenerator.getInstance("Ed25519").generateKeyPair().private, "whk_test")

    private fun transport(privateAddressesAllowed: Boolean) =
        HttpWebhookTransport({ signer }, EndpointAddresses(resolver, EndpointRules(plainHttpAllowed = true, privateAddressesAllowed = privateAddressesAllowed)))

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
}

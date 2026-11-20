package org.openfsp.gateway.webhook

import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EndpointAddressesTest {
    private val resolver = StaticHostResolver()
    private val production = EndpointAddresses(resolver, EndpointRules(developmentMode = false))
    private val development = EndpointAddresses(resolver, EndpointRules(developmentMode = true))

    @Test
    fun `a host is refused when any of its addresses is not public`() {
        resolver.addresses["mixed.example"] = listOf(InetAddress.getByName("93.184.215.14"), InetAddress.getByName("10.0.0.5"))
        assertFailsWith<UnsafeEndpointAddressException> { production.check(URI("https://mixed.example/webhooks")) }
        assertFailsWith<UnsafeEndpointAddressException> { production.resolve("mixed.example") }
    }

    @Test
    fun `resolving returns the checked addresses to connect to`() {
        resolver.addresses["shop.example"] = listOf(InetAddress.getByName("93.184.215.14"))
        assertEquals(listOf(InetAddress.getByName("93.184.215.14")), production.resolve("shop.example"))
    }

    @Test
    fun `an unknown host does not resolve`() {
        assertFailsWith<UnknownHostException> { production.resolve("nowhere.example") }
    }

    @Test
    fun `development mode allows the loopback address of the demo shop`() {
        resolver.addresses["127.0.0.1"] = listOf(InetAddress.getByName("127.0.0.1"))
        development.check(URI("http://127.0.0.1:3000/webhooks/openfsp"))
        assertEquals(listOf(InetAddress.getByName("127.0.0.1")), development.resolve("127.0.0.1"))
    }
}

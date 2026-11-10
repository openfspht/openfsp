package org.openfsp.gateway.webhook

import org.openfsp.gateway.webhook.spi.stubs.StaticHostResolver
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EndpointAddressesTest {
    private val resolver = StaticHostResolver()
    private val production = EndpointAddresses(resolver, EndpointRules(plainHttpAllowed = false, privateAddressesAllowed = false))
    private val development = EndpointAddresses(resolver, EndpointRules(plainHttpAllowed = true, privateAddressesAllowed = true))

    @Test
    fun `loopback, link-local, private, unspecified and metadata addresses are refused`() {
        listOf(
            "127.0.0.1",
            "127.1.2.3",
            "::1",
            "169.254.169.254",
            "169.254.10.1",
            "fe80::1",
            "10.0.0.5",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.10",
            "fc00::1",
            "fd12:3456::1",
            "0.0.0.0",
            "0.1.2.3",
            "::",
            "::ffff:10.0.0.1",
            "::127.0.0.1",
            "64:ff9b::a9fe:a9fe",
            "100.100.100.200",
            "224.0.0.1",
        ).forEach { address -> assertTrue(EndpointAddresses.isForbidden(InetAddress.getByName(address)), address) }
    }

    @Test
    fun `public addresses are accepted`() {
        listOf("93.184.215.14", "8.8.8.8", "172.32.0.1", "2606:4700::1111").forEach { address ->
            assertFalse(EndpointAddresses.isForbidden(InetAddress.getByName(address)), address)
        }
    }

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

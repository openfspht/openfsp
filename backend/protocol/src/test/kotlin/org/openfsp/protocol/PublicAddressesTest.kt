package org.openfsp.protocol

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PublicAddressesTest {
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
        ).forEach { address -> assertTrue(PublicAddresses.isForbidden(InetAddress.getByName(address)), address) }
    }

    @Test
    fun `public addresses are accepted`() {
        listOf("93.184.215.14", "8.8.8.8", "172.32.0.1", "2606:4700::1111").forEach { address ->
            assertFalse(PublicAddresses.isForbidden(InetAddress.getByName(address)), address)
        }
    }
}

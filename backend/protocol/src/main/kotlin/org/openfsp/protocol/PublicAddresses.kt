package org.openfsp.protocol

import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

object PublicAddresses {
    private val METADATA = setOf("169.254.169.254", "100.100.100.200", "fd00:ec2:0:0:0:0:0:254").map(InetAddress::getByName).toSet()
    private const val IPV6_ULA_MASK = 0xFE
    private const val IPV6_ULA_PREFIX = 0xFC
    private const val EMBEDDED_IPV4_OFFSET = 12
    private val NAT64_PREFIX = byteArrayOf(0, 0x64, 0xFF.toByte(), 0x9B.toByte(), 0, 0, 0, 0, 0, 0, 0, 0)
    private val IPV4_COMPATIBLE_PREFIX = ByteArray(EMBEDDED_IPV4_OFFSET)

    fun isForbidden(address: InetAddress): Boolean =
        address in METADATA ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress ||
            (address is Inet4Address && address.address[0].toInt() == 0) ||
            (address is Inet6Address && isUniqueLocal(address)) ||
            (address is Inet6Address && embeddedIpv4(address)?.let(::isForbidden) == true)

    private fun isUniqueLocal(address: Inet6Address): Boolean = address.address[0].toInt() and IPV6_ULA_MASK == IPV6_ULA_PREFIX

    private fun embeddedIpv4(address: Inet6Address): InetAddress? {
        val bytes = address.address
        val prefix = bytes.copyOfRange(0, EMBEDDED_IPV4_OFFSET)
        if (!prefix.contentEquals(IPV4_COMPATIBLE_PREFIX) && !prefix.contentEquals(NAT64_PREFIX)) return null
        return InetAddress.getByAddress(bytes.copyOfRange(EMBEDDED_IPV4_OFFSET, bytes.size))
    }
}

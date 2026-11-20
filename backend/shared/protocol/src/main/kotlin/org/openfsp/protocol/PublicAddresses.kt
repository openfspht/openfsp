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
    private const val BITS_PER_BYTE = 8
    private const val BYTE_MASK = 0xFF

    private class Range(
        network: String,
        val length: Int,
    ) {
        val prefix: ByteArray = InetAddress.getByName(network).address

        fun contains(address: InetAddress): Boolean {
            val bytes = address.address
            if (bytes.size != prefix.size) return false
            val whole = length / BITS_PER_BYTE
            if (!bytes.copyOfRange(0, whole).contentEquals(prefix.copyOfRange(0, whole))) return false
            val rest = length % BITS_PER_BYTE
            if (rest == 0) return true
            val mask = (BYTE_MASK shl (BITS_PER_BYTE - rest)) and BYTE_MASK
            return (bytes[whole].toInt() and mask) == (prefix[whole].toInt() and mask)
        }
    }

    private val RESERVED =
        listOf(
            Range("100.64.0.0", 10),
            Range("198.18.0.0", 15),
            Range("240.0.0.0", 4),
            Range("2002::", 16),
            Range("64:ff9b:1::", 48),
        )

    fun isForbidden(address: InetAddress): Boolean =
        address in METADATA ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isAnyLocalAddress ||
            address.isMulticastAddress ||
            RESERVED.any { it.contains(address) } ||
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

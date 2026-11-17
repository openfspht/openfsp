package org.openfsp.simulator.callbacks

import org.apache.hc.client5.http.DnsResolver
import org.openfsp.protocol.PublicAddresses
import java.net.InetAddress
import java.net.UnknownHostException

class CallbackAddressGuard(
    trustedHosts: Set<String>,
    private val privateAddressesAllowed: Boolean,
    private val lookup: (String) -> Array<InetAddress> = InetAddress::getAllByName,
) : DnsResolver {
    private val trusted = trustedHosts.map { it.lowercase() }.toSet()

    override fun resolve(host: String): Array<InetAddress> {
        val name = host.removePrefix("[").removeSuffix("]")
        val addresses = lookup(name)
        if (privateAddressesAllowed || name.lowercase() in trusted) return addresses
        addresses.firstOrNull(PublicAddresses::isForbidden)?.let { throw UnknownHostException("$name resolves to ${it.hostAddress}, which is not a public address") }
        return addresses
    }

    override fun resolveCanonicalHostname(host: String): String = host
}

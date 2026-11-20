package org.openfsp.gateway.webhook

import org.openfsp.ddd.DomainService
import org.openfsp.gateway.webhook.spi.HostResolver
import org.openfsp.server.PublicAddresses
import java.net.InetAddress
import java.net.URI
import java.net.UnknownHostException

class UnsafeEndpointAddressException(
    host: String,
    address: InetAddress,
) : RuntimeException("$host resolves to ${address.hostAddress}, which is not a public address")

@DomainService
class EndpointAddresses(
    private val resolver: HostResolver,
    private val rules: EndpointRules,
) {
    fun check(url: URI) {
        if (!rules.developmentMode) resolve(url.host)
    }

    fun resolve(host: String): List<InetAddress> {
        val addresses = resolver.resolve(host)
        if (addresses.isEmpty()) throw UnknownHostException(host)
        if (!rules.developmentMode) addresses.firstOrNull(PublicAddresses::isForbidden)?.let { throw UnsafeEndpointAddressException(host, it) }
        return addresses
    }
}

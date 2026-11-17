package org.openfsp.gateway.webhook.spi.stubs

import org.openfsp.ddd.Stub
import org.openfsp.gateway.webhook.spi.HostResolver
import java.net.InetAddress

@Stub
class StaticHostResolver : HostResolver {
    val addresses = mutableMapOf<String, List<InetAddress>>()

    override fun resolve(host: String): List<InetAddress> = addresses[host] ?: literal(host)

    private fun literal(host: String): List<InetAddress> = if (host.first().isDigit() || ':' in host) listOf(InetAddress.getByName(host)) else emptyList()
}

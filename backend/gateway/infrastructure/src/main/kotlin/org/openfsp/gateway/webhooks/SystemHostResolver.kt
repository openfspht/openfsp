package org.openfsp.gateway.webhooks

import org.openfsp.gateway.webhook.spi.HostResolver
import org.springframework.stereotype.Component
import java.net.InetAddress

@Component
class SystemHostResolver : HostResolver {
    override fun resolve(host: String): List<InetAddress> = InetAddress.getAllByName(host).toList()
}

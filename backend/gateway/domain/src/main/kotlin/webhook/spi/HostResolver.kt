package org.openfsp.gateway.webhook.spi

import java.net.InetAddress

fun interface HostResolver {
    fun resolve(host: String): List<InetAddress>
}

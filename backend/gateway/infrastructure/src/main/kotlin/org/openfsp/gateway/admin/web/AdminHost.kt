package org.openfsp.gateway.admin.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.springframework.stereotype.Component

@Component
class AdminHost(
    private val properties: GatewayProperties,
) {
    private val host = properties.adminHost?.let(::normalized)

    fun admits(request: HttpServletRequest): Boolean =
        when (host) {
            null -> properties.developmentMode
            else -> request.serverName?.let(::normalized) == host
        }

    private fun normalized(name: String): String = name.trim().trimEnd('.').lowercase()
}

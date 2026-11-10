package org.openfsp.gateway.security.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.configuration.GatewayProperties
import org.openfsp.gateway.web.ProblemResponses
import org.openfsp.protocol.ProblemType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

@Component
class AdminAccessFilter(
    private val properties: GatewayProperties,
    private val sessions: AdminSessions,
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !request.requestURI.startsWith("/admin/") || (request.method == "POST" && request.requestURI.trimEnd('/') == SIGN_IN)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        when {
            properties.adminToken == null && isLoopback(request) -> chain.doFilter(request, response)
            properties.adminToken == null -> response.sendError(HttpServletResponse.SC_NOT_FOUND)
            sessions.isValid(request.cookies?.firstOrNull { it.name == AdminSessions.COOKIE }?.value) -> chain.doFilter(request, response)
            else -> problems.write(OpenFspException(ProblemType.UNAUTHENTICATED), request, response)
        }
    }

    private fun isLoopback(request: HttpServletRequest): Boolean =
        runCatching { InetAddress.getByName(request.remoteAddr).isLoopbackAddress }.getOrDefault(false)

    companion object {
        const val SIGN_IN = "/admin/session"
    }
}

package org.openfsp.gateway.admin.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.admin.session.AdminSessions
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.configuration.GatewayProperties
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestPaths
import org.openfsp.protocol.ProblemType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

@Component
class AdminAccessFilter(
    private val properties: GatewayProperties,
    private val sessions: AdminSessions,
    private val problems: ProblemResponses,
    private val paths: RequestPaths,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (!paths.within(request, ADMIN)) return true
        val path = paths.canonical(request).trimEnd('/')
        return (request.method == "POST" && path == SIGN_IN) || (request.method == "GET" && path == PUBLISHED_TOKEN)
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        when {
            properties.adminToken == null && properties.developmentMode && isLoopback(request) -> chain.doFilter(request, response)
            properties.adminToken == null -> response.sendError(HttpServletResponse.SC_NOT_FOUND)
            sessions.isValid(request.cookies?.firstOrNull { it.name == AdminSessions.COOKIE }?.value) -> chain.doFilter(request, response)
            else -> problems.write(OpenFspException(ProblemType.UNAUTHENTICATED), request, response)
        }
    }

    private fun isLoopback(request: HttpServletRequest): Boolean =
        runCatching { InetAddress.getByName(request.remoteAddr).isLoopbackAddress }.getOrDefault(false)

    companion object {
        const val ADMIN = "/admin"
        const val SIGN_IN = "/admin/session"
        const val PUBLISHED_TOKEN = "/admin/session/published-token"
    }
}

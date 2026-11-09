package org.openfsp.gateway.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.GatewayProperties
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress
import java.security.MessageDigest

@Component
class AdminAccess(
    private val properties: GatewayProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/admin/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (allowed(request)) chain.doFilter(request, response) else response.sendError(HttpServletResponse.SC_NOT_FOUND)
    }

    private fun allowed(request: HttpServletRequest): Boolean {
        val token = properties.adminToken ?: return runCatching { InetAddress.getByName(request.remoteAddr).isLoopbackAddress }.getOrDefault(false)
        val presented = request.getHeader(HttpHeaders.AUTHORIZATION) ?: return false
        return MessageDigest.isEqual(presented.toByteArray(), "Bearer $token".toByteArray())
    }
}

package org.openfsp.gateway.admin

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.InetAddress

@Component
class LoopbackOnly : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith("/admin/")

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (runCatching { InetAddress.getByName(request.remoteAddr).isLoopbackAddress }.getOrDefault(false)) {
            chain.doFilter(request, response)
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
        }
    }
}

package org.openfsp.gateway.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.web.ProblemResponses
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class PublicRateLimitFilter(
    private val limits: RateLimits,
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !(path.startsWith("/.well-known/") || path.startsWith("/return/"))
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        try {
            limits.anonymous(request.remoteAddr)
        } catch (exception: OpenFspException) {
            problems.write(exception, request, response)
            return
        }
        chain.doFilter(request, response)
    }
}

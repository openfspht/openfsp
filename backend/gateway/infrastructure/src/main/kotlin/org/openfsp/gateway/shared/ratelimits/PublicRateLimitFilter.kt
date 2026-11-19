package org.openfsp.gateway.shared.ratelimits

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestPaths
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class PublicRateLimitFilter(
    private val limits: RateLimits,
    private val problems: ProblemResponses,
    private val paths: RequestPaths,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = PUBLIC_PATHS.none { paths.within(request, it) }

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

    private companion object {
        val PUBLIC_PATHS = listOf("/.well-known", "/return", "/callbacks", "/conformance")
    }
}

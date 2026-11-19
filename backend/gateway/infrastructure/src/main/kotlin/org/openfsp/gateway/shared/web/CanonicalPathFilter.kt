package org.openfsp.gateway.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class CanonicalPathFilter(
    private val problems: ProblemResponses,
    private val paths: RequestPaths,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (paths.isAmbiguous(request)) {
            problems.write(OpenFspException(ProblemType.MALFORMED_REQUEST, "The request path must not hold path parameters, encoded separators or dot segments"), request, response)
            return
        }
        chain.doFilter(request, response)
    }
}

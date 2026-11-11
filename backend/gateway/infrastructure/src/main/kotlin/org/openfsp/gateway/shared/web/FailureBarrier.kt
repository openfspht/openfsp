package org.openfsp.gateway.shared.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class FailureBarrier(
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        try {
            chain.doFilter(request, response)
        } catch (exception: Exception) {
            if (response.isCommitted) throw exception
            log.error("Unexpected failure on {} {}", request.method, RequestPaths.safe(request.requestURI), exception)
            response.reset()
            response.setHeader(RequestIds.HEADER, RequestIds.of(request))
            problems.write(OpenFspException(ProblemType.INTERNAL_ERROR), request, response)
        }
    }
}

package org.openfsp.gateway.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.ResourceIds
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIds : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val requestId = ResourceIds.generate("req")
        request.setAttribute(ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put("request_id", requestId)
        try {
            chain.doFilter(request, response)
        } finally {
            MDC.remove("request_id")
        }
    }

    companion object {
        const val HEADER = "Request-Id"
        private const val ATTRIBUTE = "openfsp.request_id"

        fun of(request: HttpServletRequest): String = request.getAttribute(ATTRIBUTE) as? String ?: ResourceIds.generate("req")
    }
}

package org.openfsp.gateway.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.GatewayProperties
import org.openfsp.protocol.ProblemType
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Clock

@Component
class RateLimits(
    properties: GatewayProperties,
    clock: Clock,
) {
    private val limits = properties.rateLimits
    val clients = RateLimiter(limits.clientPerSecond, limits.clientBurst, clock)
    val public = RateLimiter(limits.publicPerSecond, limits.publicBurst, clock)
    val synchronize = RateLimiter(1.0 / limits.synchronizeEverySeconds, limits.synchronizeBurst, clock)

    fun check(
        limiter: RateLimiter,
        key: String,
    ) {
        val wait = limiter.acquire(key) ?: return
        throw ApiException(ProblemType.RATE_LIMITED, headers = mapOf(HttpHeaders.RETRY_AFTER to wait.seconds.toString()))
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
class RateLimitFilter(
    private val limits: RateLimits,
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !(path.startsWith("/.well-known/") || path.startsWith("/return/") || path.trimEnd('/') == "/v1/webhooks/keys")
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        try {
            limits.check(limits.public, request.remoteAddr)
        } catch (exception: ApiException) {
            val rendered = problems.render(exception, request)
            response.status = rendered.statusCode.value()
            rendered.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
            response.outputStream.write(rendered.body!!)
            return
        }
        chain.doFilter(request, response)
    }
}

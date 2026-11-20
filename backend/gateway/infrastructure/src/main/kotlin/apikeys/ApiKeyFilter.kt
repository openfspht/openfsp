package org.openfsp.gateway.apikeys

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.auth.AuthenticatedKey
import org.openfsp.gateway.auth.PresentedKey
import org.openfsp.gateway.auth.api.AuthenticateKey
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.ratelimits.RateLimits
import org.openfsp.gateway.shared.web.HttpProblem
import org.openfsp.gateway.shared.web.BearerTokens
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestPaths
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.Principal

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class ApiKeyFilter(
    private val authentication: AuthenticateKey,
    private val limits: RateLimits,
    private val problems: ProblemResponses,
    private val bearer: BearerTokens,
    private val paths: RequestPaths,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !paths.isProtected(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val presented = bearer.of(request)
        presented?.let(PresentedKey::parse)?.let { request.setAttribute(PREFIX_ATTRIBUTE, it.lookupPrefix) }
        val key = presented?.let(authentication::authenticate)
        try {
            if (key == null) {
                log.info("Authentication failed for key prefix {}", presented?.let(PresentedKey::redact) ?: "none")
                limits.anonymous(request.remoteAddr)
                throw HttpProblem(ProblemType.UNAUTHENTICATED, headers = mapOf(HttpHeaders.WWW_AUTHENTICATE to "Bearer"))
            }
            request.setAttribute(ATTRIBUTE, key)
            limits.client(key.principalId)
        } catch (exception: OpenFspException) {
            problems.write(exception, request, response)
            return
        }
        chain.doFilter(Authenticated(request, key), response)
    }

    private class Authenticated(
        request: HttpServletRequest,
        private val key: AuthenticatedKey,
    ) : HttpServletRequestWrapper(request) {
        override fun getUserPrincipal(): Principal = Principal { key.principalId }
    }

    companion object {
        private const val ATTRIBUTE = "openfsp.api_key"
        private const val PREFIX_ATTRIBUTE = "openfsp.presented_prefix"

        fun of(request: HttpServletRequest): AuthenticatedKey? = request.getAttribute(ATTRIBUTE) as? AuthenticatedKey

        fun presentedPrefix(request: HttpServletRequest): String? = request.getAttribute(PREFIX_ATTRIBUTE) as? String
    }
}

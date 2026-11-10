package org.openfsp.gateway.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.auth.api.AuthenticateAKey
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.ratelimits.RateLimits
import org.openfsp.gateway.shared.web.HttpProblem
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.Principal

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class ApiKeyFilter(
    private val authentication: AuthenticateAKey,
    private val limits: RateLimits,
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = skips(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val presented = bearer(request)
        presented?.let(PresentedKey::parse)?.let { request.setAttribute(PREFIX_ATTRIBUTE, it.lookupPrefix) }
        val key = presented?.let(authentication::authenticate)
        try {
            if (key == null) {
                log.info("Authentication failed for key prefix {}", presented?.let(::prefixOf) ?: "none")
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

    private fun bearer(request: HttpServletRequest): String? =
        request
            .getHeaders(HttpHeaders.AUTHORIZATION)
            .toList()
            .singleOrNull()
            ?.takeIf { it.length > BEARER.length && it.regionMatches(0, BEARER, 0, BEARER.length, ignoreCase = true) }
            ?.substring(BEARER.length)
            ?.trim()

    private class Authenticated(
        request: HttpServletRequest,
        private val key: AuthenticatedKey,
    ) : HttpServletRequestWrapper(request) {
        override fun getUserPrincipal(): Principal = Principal { key.principalId }
    }

    companion object {
        private const val BEARER = "Bearer "
        private const val ATTRIBUTE = "openfsp.api_key"
        private const val PREFIX_ATTRIBUTE = "openfsp.presented_prefix"

        fun of(request: HttpServletRequest): AuthenticatedKey? = request.getAttribute(ATTRIBUTE) as? AuthenticatedKey

        fun presentedPrefix(request: HttpServletRequest): String? = request.getAttribute(PREFIX_ATTRIBUTE) as? String

        fun skips(request: HttpServletRequest): Boolean {
            return !request.requestURI.startsWith("/v1/")
        }

        private fun prefixOf(key: String): String = key.split('_').getOrNull(2)?.take(12)?.let { "ofsp_*_$it" } ?: "malformed"
    }
}

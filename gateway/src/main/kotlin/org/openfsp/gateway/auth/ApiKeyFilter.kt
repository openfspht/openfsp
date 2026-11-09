package org.openfsp.gateway.auth

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.web.ApiException
import org.openfsp.gateway.web.ProblemResponses
import org.openfsp.gateway.web.RequestIds
import org.openfsp.protocol.ProblemType
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.security.Principal

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class ApiKeyFilter(
    private val keys: ApiKeys,
    private val problems: ProblemResponses,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.requestURI
        return !path.startsWith("/v1/") || path.trimEnd('/') == "/v1/webhooks/keys"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val headers = request.getHeaders(HttpHeaders.AUTHORIZATION).toList()
        val presented =
            headers
                .singleOrNull()
                ?.takeIf { it.length > BEARER.length && it.regionMatches(0, BEARER, 0, BEARER.length, ignoreCase = true) }
                ?.substring(BEARER.length)
                ?.trim()
        val key = presented?.let(keys::authenticate)
        if (key == null) {
            log.info("Authentication failed for key prefix {}", presented?.let(::prefixOf) ?: "none")
            val rendered = problems.render(ApiException(ProblemType.UNAUTHENTICATED, headers = mapOf(HttpHeaders.WWW_AUTHENTICATE to "Bearer")), request)
            response.status = rendered.statusCode.value()
            rendered.headers.forEach { name, values -> values.forEach { response.addHeader(name, it) } }
            response.outputStream.write(rendered.body!!)
            return
        }
        request.setAttribute(ATTRIBUTE, key)
        chain.doFilter(Authenticated(request, key), response)
    }

    private class Authenticated(
        request: HttpServletRequest,
        private val key: AuthenticatedKey,
    ) : HttpServletRequestWrapper(request) {
        override fun getUserPrincipal(): Principal = Principal { key.principalId }
    }

    companion object {
        private const val BEARER = "Bearer "
        private const val ATTRIBUTE = "openfsp.api_key"

        fun of(request: HttpServletRequest): AuthenticatedKey? = request.getAttribute(ATTRIBUTE) as? AuthenticatedKey

        private fun prefixOf(key: String): String = key.split('_').getOrNull(2)?.take(12)?.let { "ofsp_*_$it" } ?: "malformed"
    }
}

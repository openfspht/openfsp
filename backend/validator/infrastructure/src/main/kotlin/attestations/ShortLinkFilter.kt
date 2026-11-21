package org.openfsp.validator.attestations

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.validator.attestation.AttestationSettings
import org.openfsp.validator.attestation.api.ReadAttestations
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.net.URI

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ShortLinkFilter(
    settings: AttestationSettings,
    private val attestations: ReadAttestations,
) : OncePerRequestFilter() {
    private val host = settings.shortUrl?.let { URI.create(it).host?.lowercase() }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = host == null || request.serverName.lowercase() != host

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (request.method != HttpMethod.GET.name() && request.method != HttpMethod.HEAD.name()) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED)
            return
        }
        val code = request.requestURI.removePrefix("/").removeSuffix("/")
        val target =
            if (code.isEmpty()) {
                attestations.publicUrl
            } else {
                attestations.byShortCode(code)?.let(attestations::pageUrl)
            }
        if (target == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND)
            return
        }
        response.status = HttpServletResponse.SC_MOVED_PERMANENTLY
        response.setHeader(HttpHeaders.LOCATION, target)
        response.setHeader(HttpHeaders.CACHE_CONTROL, "public, max-age=86400")
    }
}

package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component

@Component
class BearerTokens {
    fun of(request: HttpServletRequest): String? =
        request
            .getHeaders(HttpHeaders.AUTHORIZATION)
            .toList()
            .singleOrNull()
            ?.takeIf { it.length > SCHEME.length && it.regionMatches(0, SCHEME, 0, SCHEME.length, ignoreCase = true) }
            ?.substring(SCHEME.length)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val SCHEME = "Bearer "
    }
}

package org.openfsp.gateway.admin.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.common.OpenFspException
import org.openfsp.gateway.shared.web.ProblemResponses
import org.openfsp.gateway.shared.web.RequestPaths
import org.openfsp.ui.UiPaths
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
class AdminHostFilter(
    private val host: AdminHost,
    private val console: UiPaths,
    private val problems: ProblemResponses,
    private val paths: RequestPaths,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !paths.within(request, AdminAccessFilter.ADMIN) && !console.serves(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        if (host.admits(request)) {
            chain.doFilter(request, response)
        } else {
            problems.write(OpenFspException.notFound(), request, response)
        }
    }
}

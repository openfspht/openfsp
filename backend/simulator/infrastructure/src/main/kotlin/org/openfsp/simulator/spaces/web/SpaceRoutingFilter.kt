package org.openfsp.simulator.spaces.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.simulator.space.SpaceId
import org.openfsp.simulator.space.api.ManageSpaces
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class SpaceRoutingFilter(
    private val spaces: ManageSpaces,
    private val json: JsonMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !request.requestURI.startsWith(SCOPED)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val id = request.requestURI.removePrefix(SCOPED).substringBefore('/')
        val prefix = "$SCOPED$id"
        val inner = request.requestURI.removePrefix(prefix)
        if (inner.length <= 1) return chain.doFilter(request, response)
        val space = SpaceId.parse(id)?.let(spaces::enter)
        if (space == null) {
            response.status = HttpStatus.NOT_FOUND.value()
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            json.writeValue(response.outputStream, SpaceMissing())
            return
        }
        request.setAttribute(SpaceRoutes.ATTRIBUTE, space.id)
        chain.doFilter(InSpace(request, prefix), response)
    }

    private class InSpace(
        request: HttpServletRequest,
        private val prefix: String,
    ) : HttpServletRequestWrapper(request) {
        override fun getRequestURI(): String = super.getRequestURI().removePrefix(prefix)

        override fun getServletPath(): String = super.getServletPath().removePrefix(prefix)

        override fun getRequestURL(): StringBuffer = StringBuffer(super.getRequestURL().toString().replaceFirst(prefix, ""))
    }

    private companion object {
        const val SCOPED = "${SpaceRoutes.PREFIX}/"
    }
}

data class SpaceMissing(
    val error: String = "unknown_space",
    val message: String = "This simulator space does not exist or expired after inactivity. Open a new one with POST /runs.",
)

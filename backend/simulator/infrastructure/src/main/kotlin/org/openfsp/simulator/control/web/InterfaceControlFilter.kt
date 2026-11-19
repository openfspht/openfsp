package org.openfsp.simulator.control.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.simulator.shared.web.ControlProblem
import org.openfsp.ui.UiPaths
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

@Component
class InterfaceControlFilter(
    private val sessions: InterfaceSessions,
    private val access: ControlAccess,
    private val pages: UiPaths,
    private val json: JsonMapper,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !isControl(request) && !pages.isPage(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val key = sessions.controlKey
        if (!isControl(request)) {
            if (key != null) response.addHeader(HttpHeaders.SET_COOKIE, sessions.open().toString())
            chain.doFilter(request, response)
            return
        }
        try {
            if (key == null) throw ControlProblem(HttpStatus.NOT_FOUND, "The interface of this simulator has no control key")
            if (request.getHeader(FETCH_SITE)?.let { it != SAME_ORIGIN } == true) throw ControlProblem(HttpStatus.FORBIDDEN, "The control interface only answers the pages of this simulator")
            if (!sessions.isValid(request.cookies?.firstOrNull { it.name == InterfaceSessions.COOKIE }?.value)) {
                throw ControlProblem(HttpStatus.UNAUTHORIZED, "Reload the simulator interface to open a session")
            }
            request.setAttribute(ControlAccess.INTERFACE_SCOPE, access.scopeOfKey("Bearer $key"))
        } catch (problem: ControlProblem) {
            write(problem, response)
            return
        }
        chain.doFilter(request, response)
    }

    private fun isControl(request: HttpServletRequest): Boolean {
        val path = request.requestURI.removePrefix(request.contextPath)
        return path == PATH || path.startsWith("$PATH/")
    }

    private fun write(
        problem: ControlProblem,
        response: HttpServletResponse,
    ) {
        response.status = problem.status.value()
        response.contentType = MediaType.APPLICATION_PROBLEM_JSON_VALUE
        response.outputStream.write(json.writeValueAsBytes(mapOf("type" to "about:blank", "title" to problem.status.reasonPhrase, "status" to problem.status.value(), "detail" to problem.message)))
    }

    companion object {
        const val PATH = "/simulator/control"
        private const val FETCH_SITE = "Sec-Fetch-Site"
        private const val SAME_ORIGIN = "same-origin"
    }
}

package org.openfsp.simulator.controllers

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.simulator.operator.JournalEntry
import org.openfsp.simulator.operator.SimulationClock
import org.openfsp.simulator.operator.api.ControlSimulation
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
class RequestJournalFilter(
    private val control: ControlSimulation,
    private val clock: SimulationClock,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean = operatorOf(request) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val cached = ContentCachingRequestWrapper(request, MAX_BODY_BYTES)
        try {
            chain.doFilter(cached, response)
        } finally {
            val path = request.requestURI + (request.queryString?.let { "?$it" } ?: "")
            control.record(JournalEntry(operatorOf(request)!!, clock.now(), request.method, path, cached.contentAsString))
        }
    }

    private fun operatorOf(request: HttpServletRequest): String? = request.requestURI.removePrefix("/").substringBefore('/').takeIf { it.startsWith("mock_") }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

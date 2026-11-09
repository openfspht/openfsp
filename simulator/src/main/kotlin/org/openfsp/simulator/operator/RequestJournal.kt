package org.openfsp.simulator.operator

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.simulator.JournalEntry
import org.openfsp.simulator.SimulatorState
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper

@Component
class RequestJournal(
    private val state: SimulatorState,
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
            state.record(
                JournalEntry(
                    operator = operatorOf(request)!!,
                    receivedAt = state.now(),
                    method = request.method,
                    path = request.requestURI + (request.queryString?.let { "?$it" } ?: ""),
                    body = cached.contentAsString,
                ),
            )
        }
    }

    private fun operatorOf(request: HttpServletRequest): String? =
        request.requestURI
            .removePrefix("/")
            .substringBefore('/')
            .takeIf { it.startsWith("mock_") }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

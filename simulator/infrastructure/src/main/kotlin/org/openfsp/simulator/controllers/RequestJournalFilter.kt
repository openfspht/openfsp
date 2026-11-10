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
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Duration

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
        val cachedRequest = ContentCachingRequestWrapper(request, MAX_BODY_BYTES)
        val cachedResponse = ContentCachingResponseWrapper(response)
        val receivedAt = clock.now()
        try {
            chain.doFilter(cachedRequest, cachedResponse)
        } finally {
            val responseBody = String(cachedResponse.contentAsByteArray, Charsets.UTF_8).take(MAX_BODY_BYTES)
            val responseHeaders = cachedResponse.headerNames.associate { it.lowercase() to cachedResponse.getHeaders(it).joinToString(", ") }
            cachedResponse.copyBodyToResponse()
            val path = request.requestURI + (request.queryString?.let { "?${JournalRedaction.pairs(it)}" } ?: "")
            control.record(
                JournalEntry(
                    operator = operatorOf(request)!!,
                    receivedAt = receivedAt,
                    method = request.method,
                    path = path,
                    body = JournalRedaction.body(cachedRequest.contentAsString),
                    remoteAddress = request.remoteAddr,
                    headers = JournalRedaction.headers(request.headerNames.toList().associate { it.lowercase() to request.getHeaders(it).toList().joinToString(", ") }),
                    status = cachedResponse.status,
                    durationMs = Duration.between(receivedAt, clock.now()).toMillis(),
                    responseHeaders = JournalRedaction.headers(responseHeaders),
                    responseBody = JournalRedaction.body(responseBody),
                ),
            )
        }
    }

    private fun operatorOf(request: HttpServletRequest): String? = request.requestURI.removePrefix("/").substringBefore('/').takeIf { it.startsWith("mock_") }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

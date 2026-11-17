package org.openfsp.simulator.journal.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.protocol.ExchangeRedaction
import org.openfsp.simulator.common.SimulationClock
import org.openfsp.simulator.control.api.ControlSimulation
import org.openfsp.simulator.journal.JournalEntry
import org.openfsp.simulator.operator.OperatorProfile
import org.openfsp.simulator.spaces.web.SpaceRoutes
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
            val path = request.requestURI + (request.queryString?.let { "?${ExchangeRedaction.pairs(it)}" } ?: "")
            control.record(
                SpaceRoutes.of(request),
                JournalEntry(
                    operator = operatorOf(request)!!,
                    receivedAt = receivedAt,
                    method = request.method,
                    path = path,
                    body = ExchangeRedaction.body(cachedRequest.contentAsString),
                    remoteAddress = request.remoteAddr,
                    headers = ExchangeRedaction.headers(request.headerNames.toList().associate { it.lowercase() to request.getHeaders(it).toList().joinToString(", ") }),
                    status = cachedResponse.status,
                    durationMs = Duration.between(receivedAt, clock.now()).toMillis(),
                    responseHeaders = ExchangeRedaction.headers(responseHeaders),
                    responseBody = ExchangeRedaction.body(responseBody),
                ),
            )
            cachedResponse.copyBodyToResponse()
        }
    }

    private fun operatorOf(request: HttpServletRequest): String? = request.requestURI.removePrefix("/").substringBefore('/').takeIf { it.startsWith(OperatorProfile.RESERVED_PREFIX) }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

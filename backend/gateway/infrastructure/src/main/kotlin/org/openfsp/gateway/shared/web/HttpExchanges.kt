package org.openfsp.gateway.shared.web

import jakarta.servlet.http.HttpServletRequest
import org.openfsp.gateway.audit.HttpExchange
import org.openfsp.protocol.ExchangeRedaction
import org.springframework.stereotype.Component
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import java.time.Duration
import java.time.Instant

@Component
class HttpExchanges {
    fun cached(request: HttpServletRequest): ContentCachingRequestWrapper = ContentCachingRequestWrapper(request, MAX_BODY_BYTES)

    fun of(
        request: ContentCachingRequestWrapper,
        response: ContentCachingResponseWrapper,
        startedAt: Instant,
        endedAt: Instant,
    ): HttpExchange =
        HttpExchange(
            remoteAddress = request.remoteAddr,
            durationMs = Duration.between(startedAt, endedAt).toMillis(),
            requestHeaders = ExchangeRedaction.headers(headersOf(request)),
            requestBody = ExchangeRedaction.body(request.contentAsString.take(MAX_BODY_BYTES)),
            responseHeaders = ExchangeRedaction.headers(response.headerNames.associate { it.lowercase() to response.getHeaders(it).joinToString(", ") }),
            responseBody = ExchangeRedaction.body(String(response.contentAsByteArray, Charsets.UTF_8).take(MAX_BODY_BYTES)),
        )

    private fun headersOf(request: HttpServletRequest): Map<String, String> = request.headerNames.toList().associate { it.lowercase() to request.getHeaders(it).toList().joinToString(", ") }

    private companion object {
        const val MAX_BODY_BYTES = 64 * 1024
    }
}

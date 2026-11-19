package org.openfsp.gateway.auditing.web

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.openfsp.gateway.apikeys.web.ApiKeyFilter
import org.openfsp.gateway.audit.AuditKind
import org.openfsp.gateway.audit.AuditRecord
import org.openfsp.gateway.audit.spi.AuditTrail
import org.openfsp.gateway.common.BusinessCalendar
import org.openfsp.gateway.shared.web.HttpExchanges
import org.openfsp.gateway.shared.web.RequestIds
import org.openfsp.gateway.shared.web.RequestPaths
import org.slf4j.LoggerFactory
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.util.ContentCachingResponseWrapper

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
class AuditFilter(
    private val audit: AuditTrail,
    private val calendar: BusinessCalendar,
    private val paths: RequestPaths,
    private val exchanges: HttpExchanges,
) : OncePerRequestFilter() {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun shouldNotFilter(request: HttpServletRequest): Boolean = !paths.isProtected(request)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val cachedRequest = exchanges.cached(request)
        val cachedResponse = ContentCachingResponseWrapper(response)
        val startedAt = calendar.now()
        try {
            chain.doFilter(cachedRequest, cachedResponse)
        } finally {
            val exchange = exchanges.of(cachedRequest, cachedResponse, startedAt, calendar.now())
            cachedResponse.copyBodyToResponse()
            val key = ApiKeyFilter.of(request)
            val pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE) as? String
            record(
                AuditRecord(
                    at = calendar.now(),
                    kind = if (key == null) AuditKind.AUTHENTICATION_FAILURE else AuditKind.REQUEST,
                    principalId = key?.principalId,
                    lookupPrefix = key?.lookupPrefix ?: ApiKeyFilter.presentedPrefix(request),
                    operation = "${request.method} ${pattern ?: paths.safe(paths.canonical(request))}",
                    requestId = RequestIds.of(request),
                    result = cachedResponse.status.toString(),
                    exchange = exchange,
                ),
            )
        }
    }

    private fun record(record: AuditRecord) {
        runCatching { audit.record(record) }.onFailure { log.error("Audit record of {} not written", record.operation, it) }
    }
}
